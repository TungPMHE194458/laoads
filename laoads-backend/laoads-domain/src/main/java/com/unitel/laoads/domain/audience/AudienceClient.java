package com.unitel.laoads.domain.audience;

import java.util.List;
import java.util.Map;

/**
 * Port gọi Audience API (OCS Audience / BCCS3 Audience). Cài đặt ở laoads-infra: {@code HttpAudienceClient}
 * (gọi thật, header x-api-key) hoặc {@code SimulatedAudienceClient} (dev, dựng dữ liệu từ LADS_SUBSCRIBER),
 * chọn bằng {@code laoads.audience.client}. Lỗi kết nối / xác thực ném {@link AudienceException}.
 */
public interface AudienceClient {

    /** "http" | "simulated" — hiển thị cho người dùng. */
    String mode();

    /** Nguồn đã đủ cấu hình (URL + API key) để gọi chưa. */
    boolean configured(Audience.Source source);

    List<String> tables(Audience.Source source);

    /** Cột của bảng; kiểu dữ liệu để nguyên như API trả (service tự chuẩn hoá). */
    List<RawColumn> columns(Audience.Source source, String table);

    Audience.VerifyResult verify(Audience.Source source, Audience.Request request);

    /**
     * Stream NDJSON: gọi {@code handler} cho từng dòng (key = tên cột / targetField như API trả).
     * {@code offset} chỉ dùng cho OCS (bỏ qua dòng đã nạp); BCCS3 luôn từ đầu.
     *
     * @return số dòng đã đọc trong lần gọi này
     */
    long stream(Audience.Source source, Audience.Request request, long offset, RowHandler handler);

    record RawColumn(String name, String dataType) {
    }

    @FunctionalInterface
    interface RowHandler {
        void row(Map<String, Object> row);
    }
}
