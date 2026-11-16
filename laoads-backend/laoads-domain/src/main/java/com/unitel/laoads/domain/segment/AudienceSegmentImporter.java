package com.unitel.laoads.domain.segment;

import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.AudienceClient;
import com.unitel.laoads.domain.audience.AudienceException;
import com.unitel.laoads.domain.common.Isdns;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.engine.Utc;
import com.unitel.laoads.domain.segment.SegmentEnums.Status;
import com.unitel.laoads.domain.segment.SegmentEnums.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Nạp tệp AUDIENCE (job nền): stream NDJSON từ Audience API, lấy cột PHONE, chuẩn hoá số thuê bao (856…),
 * bỏ trùng, ghi LADS_SEGMENT_MEMBER theo lô. Mỗi lô ghi thành viên + IMPORTED_ROWS (offset) trong CÙNG một
 * transaction nên offset luôn khớp dữ liệu đã nạp: stream OCS đứt giữa chừng thì lần chạy lại nạp tiếp từ offset,
 * không mất / không trùng. BCCS3 không có offset nên luôn nạp lại từ đầu.
 * Không chạy trong transaction dài — stream có thể kéo dài nhiều phút. Giả định 1 instance BE (như các job engine).
 */
@Service
@Slf4j
public class AudienceSegmentImporter {

    static final int BATCH = 1000;
    /** Cập nhật TOTAL_USERS (COUNT) sau mỗi N lô để màn hình thấy tiến độ mà không đếm lại quá thường xuyên. */
    static final int PROGRESS_EVERY = 5;

    private final SegmentRepository repo;
    private final SegmentService segmentService;
    private final AudienceClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AudienceSegmentImporter(SegmentRepository repo, SegmentService segmentService, AudienceClient client,
                                   JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.segmentService = segmentService;
        this.client = client;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    public void build(long segmentId) {
        Segment s = repo.findById(segmentId).orElse(null);
        if (s == null || s.getStatus() != Status.BUILDING || s.getType() != Type.AUDIENCE) {
            return;
        }
        Audience.Source source;
        Audience.Request request = segmentService.audienceOf(s);
        try {
            source = Audience.Source.valueOf(s.getSource().name());
        } catch (IllegalArgumentException e) {
            fail(segmentId, "Nguồn " + s.getSource() + " không phải Audience API", 0);
            return;
        }
        if (request == null || request.phoneColumn() == null) {
            fail(segmentId, "Request Audience hỏng hoặc thiếu cột PHONE", 0);
            return;
        }
        long offset = source.supportsOffset() ? Math.max(0, s.getImportedRows() == null ? 0 : s.getImportedRows()) : 0;
        if (offset == 0) {
            tx.executeWithoutResult(st -> {
                jdbc.update("DELETE FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?", segmentId);
                jdbc.update("UPDATE LADS_SEGMENT SET IMPORTED_ROWS = 0, TOTAL_USERS = 0 WHERE ID = ?", segmentId);
            });
        }
        Batch batch = new Batch(segmentId, offset, request.phoneColumn().columnName());
        log.info("[AUDIENCE] nạp tệp #{} từ {} bảng {} (offset {})", segmentId, source, request.tableName(), offset);
        try {
            client.stream(source, request, offset, batch::accept);
            batch.flush();
            finish(segmentId, batch);
        } catch (RuntimeException e) {
            // ghi nốt các dòng đã đọc để offset không bỏ sót; nếu chính DB lỗi thì offset giữ ở lô trước
            try {
                batch.flush();
            } catch (RuntimeException flushError) {
                log.warn("[AUDIENCE] tệp #{} không ghi được lô cuối: {}", segmentId, flushError.getMessage());
            }
            String reason = e instanceof AudienceException ae ? ae.kind() + ": " + ae.getMessage() : String.valueOf(e.getMessage());
            fail(segmentId, reason, batch.committedRows);
            log.warn("[AUDIENCE] tệp #{} lỗi sau {} dòng: {}", segmentId, batch.committedRows, reason);
        }
    }

    private void finish(long segmentId, Batch b) {
        tx.executeWithoutResult(st -> jdbc.update("UPDATE LADS_SEGMENT SET STATUS = 'READY', ERROR_MESSAGE = NULL, "
                        + "IMPORTED_ROWS = ?, TOTAL_USERS = (SELECT COUNT(*) FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?), "
                        + "UPDATED_AT = ? WHERE ID = ? AND STATUS = 'BUILDING'",
                b.committedRows, segmentId, Utc.ts(Instant.now()), segmentId));
        log.info("[AUDIENCE] nạp xong tệp #{}: {} dòng, {} số không hợp lệ", segmentId, b.committedRows, b.invalid);
    }

    private void fail(long segmentId, String reason, long committedRows) {
        String msg = Texts.trimToNull(reason);
        String trimmed = msg == null ? "Lỗi không rõ" : msg.length() > 1000 ? msg.substring(0, 1000) : msg;
        tx.executeWithoutResult(st -> jdbc.update("UPDATE LADS_SEGMENT SET STATUS = 'FAILED', ERROR_MESSAGE = ?, "
                        + "IMPORTED_ROWS = ?, TOTAL_USERS = (SELECT COUNT(*) FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?), "
                        + "UPDATED_AT = ? WHERE ID = ?",
                trimmed, committedRows, segmentId, Utc.ts(Instant.now()), segmentId));
    }

    /** Lấy số thuê bao từ 1 dòng NDJSON: ưu tiên key PHONE (targetField), sau đó tên cột gốc (không phân biệt hoa thường). */
    static Optional<String> phoneOf(Map<String, Object> row, String phoneColumn) {
        Object v = row.get(Audience.PHONE);
        if (v == null) {
            v = row.get(phoneColumn);
        }
        if (v == null) {
            String lower = phoneColumn.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && (e.getKey().equalsIgnoreCase(Audience.PHONE) || e.getKey().toLowerCase(Locale.ROOT).equals(lower))) {
                    v = e.getValue();
                    break;
                }
            }
        }
        return v == null ? Optional.empty() : Isdns.normalize(v instanceof Number n ? String.valueOf(n.longValue()) : v.toString());
    }

    /** Gom dòng thành lô; mỗi lần ghi lô là 1 transaction (thành viên + offset). */
    private final class Batch {
        final long segmentId;
        final String phoneColumn;
        final Set<String> phones = new LinkedHashSet<>();
        long readRows;
        long committedRows;
        long invalid;
        int flushes;

        Batch(long segmentId, long offset, String phoneColumn) {
            this.segmentId = segmentId;
            this.readRows = offset;
            this.committedRows = offset;
            this.phoneColumn = phoneColumn;
        }

        void accept(Map<String, Object> row) {
            readRows++;
            Optional<String> phone = phoneOf(row, phoneColumn);
            if (phone.isPresent()) {
                phones.add(phone.get());
            } else {
                invalid++;
            }
            if (phones.size() >= BATCH) {
                flush();
            }
        }

        void flush() {
            if (readRows == committedRows) {
                return;
            }
            List<Object[]> rows = new ArrayList<>(phones.size());
            for (String p : phones) {
                rows.add(new Object[]{segmentId, p, segmentId, p});
            }
            long upTo = readRows;
            boolean progress = ++flushes % PROGRESS_EVERY == 0;
            tx.executeWithoutResult(st -> {
                if (!rows.isEmpty()) {
                    // bỏ trùng với dữ liệu đã nạp (lô trước / lần chạy trước); trong lô đã bỏ trùng bằng Set
                    jdbc.batchUpdate("INSERT INTO LADS_SEGMENT_MEMBER (SEGMENT_ID, ISDN) SELECT ?, ? FROM DUAL "
                            + "WHERE NOT EXISTS (SELECT 1 FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ? AND ISDN = ?)", rows);
                }
                if (progress) {
                    jdbc.update("UPDATE LADS_SEGMENT SET IMPORTED_ROWS = ?, TOTAL_USERS = (SELECT COUNT(*) FROM "
                            + "LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?) WHERE ID = ? AND STATUS = 'BUILDING'", upTo, segmentId, segmentId);
                } else {
                    jdbc.update("UPDATE LADS_SEGMENT SET IMPORTED_ROWS = ? WHERE ID = ? AND STATUS = 'BUILDING'", upTo, segmentId);
                }
            });
            committedRows = upTo;
            phones.clear();
        }
    }
}
