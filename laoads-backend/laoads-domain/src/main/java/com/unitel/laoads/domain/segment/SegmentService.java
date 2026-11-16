package com.unitel.laoads.domain.segment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.AudienceRequests;
import com.unitel.laoads.domain.audience.AudienceService;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.segment.SegmentEnums.Source;
import com.unitel.laoads.domain.segment.SegmentEnums.Status;
import com.unitel.laoads.domain.segment.SegmentEnums.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tệp khách hàng (SRS 3.2.2, 3.5): tạo từ thuộc tính (QUERY, build bất đồng bộ) hoặc từ file (FILE). */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentService {

    public record ImportResult(Segment segment, int invalidRows) {
    }

    public record MemberRow(String isdn, String province, String packageCode, BigDecimal gVolume) {
    }

    public record BulkDeleteResult(int deleted, List<Failure> failed) {
        public record Failure(long id, String reason) {
        }
    }

    private static final int BATCH = 1000;
    private static final TypeReference<List<SegmentCondition>> CONDITIONS = new TypeReference<>() {
    };

    private final SegmentRepository repo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AudienceService audienceService;

    // ───────────── Đọc ─────────────

    @Transactional(readOnly = true)
    public Page<Segment> list(long workspaceId, String query, Type type, Status status, Pageable pageable) {
        return repo.search(workspaceId, Texts.likePattern(query), type, status, pageable);
    }

    @Transactional(readOnly = true)
    public Segment get(long workspaceId, long id) {
        return repo.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> notFound(id));
    }

    /** Số chiến dịch đang tham chiếu từng tệp (để chặn xoá + hiển thị referencedBy). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> referenceCounts(Collection<Long> segmentIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (segmentIds.isEmpty()) {
            return out;
        }
        List<Long> ids = List.copyOf(segmentIds);
        String in = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbc.query("SELECT SEGMENT_ID, COUNT(*) FROM LADS_CAMPAIGN WHERE SEGMENT_ID IN (" + in + ") GROUP BY SEGMENT_ID",
                rs -> {
                    out.put(rs.getLong(1), rs.getInt(2));
                }, ids.toArray());
        return out;
    }

    public List<SegmentCondition> conditionsOf(Segment s) {
        if (s.getConditionsJson() == null || s.getConditionsJson().isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(s.getConditionsJson(), CONDITIONS);
        } catch (JsonProcessingException e) {
            log.warn("[SEGMENT] conditionsJson hỏng ở segment #{}: {}", s.getId(), e.getMessage());
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public long estimate(List<SegmentCondition> conditions) {
        SegmentQueryBuilder.Where w = SegmentQueryBuilder.build(conditions);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM LADS_SUBSCRIBER WHERE " + w.sql(), Long.class,
                w.params().toArray());
        return n == null ? 0 : n;
    }

    @Transactional(readOnly = true)
    public Page<MemberRow> members(long workspaceId, long id, Pageable pageable) {
        Segment s = get(workspaceId, id);
        if (!s.isReady()) {
            throw new LaoadsException(ErrorCode.SEGMENT_NOT_READY, "Tệp khách hàng chưa sẵn sàng");
        }
        long offset = pageable.getOffset();
        long limit = offset + pageable.getPageSize();
        List<MemberRow> rows = jdbc.query(
                "SELECT * FROM (SELECT a.*, ROWNUM RN FROM ("
                        + "SELECT m.ISDN, sb.PROVINCE_CODE_ACTIVE, sb.REAL_PACKAGE_DATA, sb.G_VOLUME "
                        + "FROM LADS_SEGMENT_MEMBER m LEFT JOIN LADS_SUBSCRIBER sb ON sb.ISDN = m.ISDN "
                        + "WHERE m.SEGMENT_ID = ? ORDER BY m.ISDN) a WHERE ROWNUM <= ?) WHERE RN > ?",
                (rs, i) -> new MemberRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)),
                id, limit, offset);
        return new PageImpl<>(rows, pageable, s.getTotalUsers());
    }

    // ───────────── Tạo ─────────────

    @Transactional
    public Segment createQuery(long workspaceId, String name, String description, List<SegmentCondition> conditions,
                               String actor) {
        String n = requireName(workspaceId, name);
        SegmentQueryBuilder.build(conditions); // validate sớm (400 nếu điều kiện sai)
        Segment s = newSegment(workspaceId, n, description, Type.QUERY, Source.DWH, actor);
        s.setStatus(Status.BUILDING);
        s.setConditionsJson(toJson(conditions == null ? List.of() : conditions));
        s = repo.save(s);
        log.info("[SEGMENT] {} tạo tệp QUERY #{} '{}' ({} điều kiện) — chờ build", actor, s.getId(), n,
                conditions == null ? 0 : conditions.size());
        return s;
    }

    @Transactional
    public ImportResult importFile(long workspaceId, String name, String description, String fileName,
                                   InputStream content, String actor) {
        String n = requireName(workspaceId, name);
        IsdnFileParser.Result parsed = IsdnFileParser.parse(fileName, content);
        Segment s = newSegment(workspaceId, n, description, Type.FILE, Source.FILE, actor);
        s.setStatus(Status.READY);
        s.setFileName(Texts.trimToNull(fileName));
        s.setTotalUsers(parsed.isdns().size());
        s = repo.saveAndFlush(s);
        insertMembers(s.getId(), parsed.isdns());
        log.info("[SEGMENT] {} import tệp FILE #{} '{}': {} số hợp lệ, bỏ {} dòng sai", actor, s.getId(), n,
                parsed.isdns().size(), parsed.invalidRows());
        return new ImportResult(s, parsed.invalidRows());
    }

    /** Tệp CAMPAIGN_OUTCOME: khách hàng nhận tin nhưng chưa chuyển đổi của một chiến dịch (gửi lại vòng sau). */
    @Transactional
    public Segment createCampaignOutcome(long workspaceId, long campaignId, String campaignName) {
        String base = "KH chưa đạt mục tiêu – " + campaignName;
        String n = base.length() > 230 ? base.substring(0, 230) : base;
        if (repo.countByName(workspaceId, n.toLowerCase(Locale.ROOT)) > 0) {
            n = n + " (" + campaignId + ")";
        }
        Segment s = newSegment(workspaceId, n, "Tự sinh từ kết quả chiến dịch (GOAL_NOT_ACHIEVED)",
                Type.CAMPAIGN_OUTCOME, Source.CAMPAIGN, "system");
        s.setStatus(Status.READY);
        s.setSourceCampaignId(campaignId);
        s = repo.saveAndFlush(s);
        int count = jdbc.update("INSERT INTO LADS_SEGMENT_MEMBER (SEGMENT_ID, ISDN) "
                + "SELECT DISTINCT ?, ISDN FROM LADS_CAMPAIGN_TARGET WHERE CAMPAIGN_ID = ? AND IS_CONTROL = 0 "
                + "AND CONVERTED = 0", s.getId(), campaignId);
        s.setTotalUsers(count);
        return s;
    }

    /**
     * Tệp AUDIENCE: kiểm tra request (cục bộ + verify phía service) rồi tạo tệp BUILDING; job nền
     * ({@link AudienceSegmentImporter}) stream NDJSON và nạp số thuê bao.
     */
    @Transactional
    public Segment createAudience(long workspaceId, String name, String description, Audience.Source source,
                                  AudienceRequests.RawRequest raw, String actor) {
        String n = requireName(workspaceId, name);
        Audience.Request request = audienceService.requireValid(source, raw);
        Segment s = newSegment(workspaceId, n, description, Type.AUDIENCE, Source.valueOf(source.name()), actor);
        s.setStatus(Status.BUILDING);
        s.setAudienceJson(toJson(request));
        s.setImportedRows(0L);
        s = repo.save(s);
        log.info("[SEGMENT] {} tạo tệp AUDIENCE #{} '{}' từ {} bảng {} — chờ nạp", actor, s.getId(), n, source,
                request.tableName());
        return s;
    }

    /** Request Audience của tệp AUDIENCE (null với loại khác). */
    public Audience.Request audienceOf(Segment s) {
        if (s.getAudienceJson() == null || s.getAudienceJson().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(s.getAudienceJson(), Audience.Request.class);
        } catch (JsonProcessingException e) {
            log.warn("[SEGMENT] AUDIENCE_JSON hỏng ở tệp #{}: {}", s.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Chạy lại tệp FAILED loại AUDIENCE / QUERY. OCS nạp tiếp từ số dòng đã xử lý (offset);
     * BCCS3 không có offset nên nạp lại từ đầu. Loại / trạng thái khác → 409 INVALID_TRANSITION.
     */
    @Transactional
    public Segment retry(long workspaceId, long id, String actor) {
        Segment s = get(workspaceId, id);
        if (s.getStatus() != Status.FAILED || (s.getType() != Type.AUDIENCE && s.getType() != Type.QUERY)) {
            throw new LaoadsException(ErrorCode.INVALID_TRANSITION, "Chỉ chạy lại được tệp lỗi loại AUDIENCE hoặc QUERY");
        }
        if (s.getType() == Type.AUDIENCE && s.getSource() != Source.OCS) {
            s.setImportedRows(0L);
        }
        s.setStatus(Status.BUILDING);
        s.setErrorMessage(null);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(actor);
        log.info("[SEGMENT] {} chạy lại tệp {} #{} (offset {})", actor, s.getType(), id, s.getImportedRows());
        return s;
    }

    @Transactional(readOnly = true)
    public List<Long> pendingAudienceBuilds() {
        return repo.findTop20ByStatusAndTypeOrderByIdAsc(Status.BUILDING, Type.AUDIENCE).stream().map(Segment::getId).toList();
    }

    // ───────────── Build tệp QUERY (job nền) ─────────────

    @Transactional(readOnly = true)
    public List<Long> pendingQueryBuilds() {
        return repo.findTop20ByStatusAndTypeOrderByIdAsc(Status.BUILDING, Type.QUERY).stream().map(Segment::getId).toList();
    }

    @Transactional
    public void buildQuerySegment(long id) {
        Segment s = repo.findById(id).orElse(null);
        if (s == null || s.getStatus() != Status.BUILDING) {
            return;
        }
        try {
            SegmentQueryBuilder.Where w = SegmentQueryBuilder.build(conditionsOf(s));
            jdbc.update("DELETE FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?", id);
            List<Object> params = new ArrayList<>();
            params.add(id);
            params.addAll(w.params());
            int count = jdbc.update("INSERT INTO LADS_SEGMENT_MEMBER (SEGMENT_ID, ISDN) SELECT ?, ISDN FROM LADS_SUBSCRIBER "
                    + "WHERE " + w.sql(), params.toArray());
            s.setTotalUsers(count);
            s.setStatus(Status.READY);
            s.setErrorMessage(null);
            log.info("[SEGMENT] build xong tệp QUERY #{}: {} thuê bao", id, count);
        } catch (RuntimeException e) {
            s.setStatus(Status.FAILED);
            s.setErrorMessage(Texts.trimToNull(String.valueOf(e.getMessage())));
            log.warn("[SEGMENT] build tệp #{} thất bại: {}", id, e.getMessage());
        }
        s.setUpdatedAt(Instant.now());
    }

    // ───────────── Xoá ─────────────

    @Transactional
    public BulkDeleteResult bulkDelete(long workspaceId, List<Long> ids) {
        List<BulkDeleteResult.Failure> failed = new ArrayList<>();
        Map<Long, Integer> refs = referenceCounts(ids);
        int deleted = 0;
        for (Long id : ids) {
            var s = repo.findByIdAndWorkspaceId(id, workspaceId);
            if (s.isEmpty()) {
                failed.add(new BulkDeleteResult.Failure(id, "Không tồn tại"));
            } else if (refs.getOrDefault(id, 0) > 0) {
                failed.add(new BulkDeleteResult.Failure(id, "Đang được chiến dịch sử dụng"));
            } else {
                jdbc.update("DELETE FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?", id);
                repo.delete(s.get());
                deleted++;
            }
        }
        return new BulkDeleteResult(deleted, failed);
    }

    // ───────────── tiện ích ─────────────

    private String requireName(long workspaceId, String name) {
        String n = Texts.trimToNull(name);
        if (n == null || n.length() > 255) {
            throw LaoadsException.validation(List.of(new FieldViolation("name", "error.validation.invalidValue",
                    "Giá trị không hợp lệ")));
        }
        if (repo.countByName(workspaceId, n.toLowerCase(Locale.ROOT)) > 0) {
            throw new LaoadsException(ErrorCode.SEGMENT_NAME_DUPLICATE, "Tên tệp khách hàng đã tồn tại trong workspace");
        }
        return n;
    }

    private static Segment newSegment(long workspaceId, String name, String description, Type type, Source source,
                                      String actor) {
        Segment s = new Segment();
        s.setWorkspaceId(workspaceId);
        s.setName(name);
        s.setDescription(Texts.trimToNull(description));
        s.setType(type);
        s.setSource(source);
        s.setTotalUsers(0);
        s.setCreatedAt(Instant.now());
        s.setCreatedBy(actor);
        return s;
    }

    private void insertMembers(long segmentId, Collection<String> isdns) {
        List<Object[]> batch = new ArrayList<>(BATCH);
        for (String isdn : isdns) {
            batch.add(new Object[]{segmentId, isdn});
            if (batch.size() == BATCH) {
                jdbc.batchUpdate("INSERT INTO LADS_SEGMENT_MEMBER (SEGMENT_ID, ISDN) VALUES (?, ?)", batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO LADS_SEGMENT_MEMBER (SEGMENT_ID, ISDN) VALUES (?, ?)", batch);
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LaoadsException notFound(long id) {
        return LaoadsException.notFound("error.notFound.segment", "Không tìm thấy tệp khách hàng", id);
    }
}
