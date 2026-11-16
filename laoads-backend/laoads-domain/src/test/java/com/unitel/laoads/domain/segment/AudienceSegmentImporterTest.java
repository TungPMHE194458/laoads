package com.unitel.laoads.domain.segment;

import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.AudienceClient;
import com.unitel.laoads.domain.audience.AudienceException;
import com.unitel.laoads.domain.segment.SegmentEnums.Source;
import com.unitel.laoads.domain.segment.SegmentEnums.Status;
import com.unitel.laoads.domain.segment.SegmentEnums.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudienceSegmentImporterTest {

    @Mock
    SegmentRepository repo;
    @Mock
    SegmentService segmentService;
    @Mock
    AudienceClient client;
    @Mock
    JdbcTemplate jdbc;
    @Mock
    PlatformTransactionManager txManager;

    AudienceSegmentImporter importer;
    final List<Object[]> inserted = new ArrayList<>();

    static final Audience.Request REQUEST = new Audience.Request("all_report_stl_new", List.of(
            new Audience.SelectedColumn("phone_number", Audience.DataType.STRING, "PHONE"),
            new Audience.SelectedColumn("age", Audience.DataType.INT, "AGE")), List.of());

    @BeforeEach
    void setUp() {
        importer = new AudienceSegmentImporter(repo, segmentService, client, jdbc, txManager);
        when(segmentService.audienceOf(any())).thenReturn(REQUEST);
        when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(inv -> {
            List<Object[]> rows = inv.getArgument(1);
            inserted.addAll(rows);
            return new int[rows.size()];
        });
    }

    private Segment segment(Source source, long importedRows) {
        Segment s = new Segment();
        s.setId(5L);
        s.setType(Type.AUDIENCE);
        s.setSource(source);
        s.setStatus(Status.BUILDING);
        s.setImportedRows(importedRows);
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        return s;
    }

    private static Map<String, Object> row(Object phone) {
        Map<String, Object> m = new HashMap<>();
        m.put("phone_number", phone);
        m.put("age", 30);
        return m;
    }

    @Test
    @DisplayName("nạp theo lô: chuẩn hoá số, bỏ trùng / số sai, kết thúc READY với số dòng đã xử lý")
    void importsInBatches() {
        segment(Source.OCS, 0);
        when(client.stream(eq(Audience.Source.OCS), eq(REQUEST), eq(0L), any())).thenAnswer(inv -> {
            AudienceClient.RowHandler h = inv.getArgument(3);
            for (int i = 0; i < 2500; i++) {
                h.row(row("020" + String.format("%08d", i)));
            }
            h.row(row("020" + String.format("%08d", 2100))); // trùng trong cùng lô → bỏ; trùng khác lô do NOT EXISTS lo
            h.row(row("abc"));                             // không hợp lệ
            h.row(row(8562055510002L));                    // kiểu số
            return 2503L;
        });

        importer.build(5);

        assertThat(inserted).hasSize(2501);
        assertThat(inserted.get(0)[1]).isEqualTo("8562000000000");
        assertThat(inserted).extracting(r -> r[1]).doesNotHaveDuplicates().contains("8562055510002");
        verify(jdbc).update(startsWith("DELETE FROM LADS_SEGMENT_MEMBER"), eq(5L));
        verify(jdbc).update(startsWith("UPDATE LADS_SEGMENT SET STATUS = 'READY'"), eq(2503L), eq(5L), any(), eq(5L));
    }

    @Test
    @DisplayName("stream đứt giữa chừng: ghi nốt lô đang đọc, FAILED với offset = số dòng đã ghi")
    void failsWithCommittedOffset() {
        segment(Source.OCS, 0);
        when(client.stream(any(), any(), anyLong(), any())).thenAnswer(inv -> {
            AudienceClient.RowHandler h = inv.getArgument(3);
            for (int i = 0; i < 1500; i++) {
                h.row(row("020" + String.format("%08d", i)));
            }
            throw new AudienceException(AudienceException.Kind.UNAVAILABLE, "Connection reset");
        });

        importer.build(5);

        assertThat(inserted).hasSize(1500);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(startsWith("UPDATE LADS_SEGMENT SET STATUS = 'FAILED'"), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture());
        assertThat((String) args.getAllValues().get(0)).contains("UNAVAILABLE").contains("Connection reset");
        assertThat(args.getAllValues().get(1)).isEqualTo(1500L);
    }

    @Test
    @DisplayName("OCS chạy lại: stream từ offset đã ghi, không xoá dữ liệu cũ")
    void ocsResumesFromOffset() {
        segment(Source.OCS, 1500);
        when(client.stream(any(), any(), anyLong(), any())).thenReturn(0L);

        importer.build(5);

        verify(client).stream(eq(Audience.Source.OCS), eq(REQUEST), eq(1500L), any());
        verify(jdbc, never()).update(startsWith("DELETE FROM LADS_SEGMENT_MEMBER"), eq(5L));
    }

    @Test
    @DisplayName("BCCS3 không có offset: luôn nạp lại từ đầu")
    void bccs3RestartsFromZero() {
        segment(Source.BCCS3, 1500);
        when(client.stream(any(), any(), anyLong(), any())).thenReturn(0L);

        importer.build(5);

        verify(client).stream(eq(Audience.Source.BCCS3), eq(REQUEST), eq(0L), any());
        verify(jdbc, atLeastOnce()).update(startsWith("DELETE FROM LADS_SEGMENT_MEMBER"), eq(5L));
    }

    @Test
    @DisplayName("lấy số từ key PHONE (targetField) hoặc tên cột gốc, không phân biệt hoa thường")
    void phoneOf() {
        assertThat(AudienceSegmentImporter.phoneOf(Map.of("PHONE", "02055510002"), "msisdn")).contains("8562055510002");
        assertThat(AudienceSegmentImporter.phoneOf(Map.of("MSISDN", "2055510002"), "msisdn")).contains("8562055510002");
        assertThat(AudienceSegmentImporter.phoneOf(Map.of("other", "1"), "msisdn")).isEmpty();
    }
}
