package com.unitel.laoads.domain.campaign;

import java.util.List;
import java.util.Locale;

/**
 * Danh mục template sự kiện cho chiến dịch chạy theo sự kiện (SRS 3.2.1.6). Hệ thống ngoài gọi
 * {@code POST /v1/events} với {@code eventType} = mã template để ghi danh thuê bao vào các chiến dịch đang chạy.
 * Nhãn hiển thị nằm ở FE ({@code campaign.event.<code>}).
 */
public final class EventTemplates {

    public static final List<String> CODES = List.of("SIM_ACTIVATION_D0", "TOPUP_FIRST_TIME", "PACKAGE_EXPIRING_D3", "BIRTHDAY");

    private EventTemplates() {
    }

    /** Mã chuẩn hoá (in hoa, bỏ khoảng trắng); null nếu trống. */
    public static String normalize(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean exists(String code) {
        return code != null && CODES.contains(normalize(code));
    }
}
