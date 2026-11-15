package com.unitel.laoads.domain.settings;

import java.time.LocalTime;

/**
 * Cấu hình gửi tin toàn hệ thống: retry (yêu cầu nâng cấp 4.0), hoàn tài nguyên khi gửi lỗi,
 * khung giờ không gửi mặc định (yêu cầu 9.0), ngôn ngữ mặc định.
 */
public record NotificationSettings(
        int retryMaxAttempts,
        int retryIntervalMinutes,
        boolean refundOnFailure,
        boolean silentHoursEnabled,
        String silentFrom,
        String silentTo,
        String defaultLanguage
) {

    public static final NotificationSettings DEFAULTS =
            new NotificationSettings(3, 30, true, true, "21:00", "07:00", "LO");

    public LocalTime silentFromTime() {
        return LocalTime.parse(silentFrom);
    }

    public LocalTime silentToTime() {
        return LocalTime.parse(silentTo);
    }
}
