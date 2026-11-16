package com.unitel.laoads.domain.settings;

import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chính sách gửi tin chung (retry, hoàn tài nguyên, khung giờ không gửi, ngôn ngữ mặc định).
 * Đọc: mọi user đăng nhập; ghi: SUPER_ADMIN (kiểm tra ở controller). Đầu số: xem {@link SenderNumberService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    static final String PREFIX = "notification.";
    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Set<String> LANGUAGES = Set.of("LO", "EN", "VI");

    private final AppSettingRepository settingRepo;

    // ───────────── Cấu hình gửi tin ─────────────

    @Transactional(readOnly = true)
    public NotificationSettings notification() {
        Map<String, String> m = new HashMap<>();
        settingRepo.findByKeyStartingWith(PREFIX)
                .forEach(s -> m.put(s.getKey().substring(PREFIX.length()), s.getValue()));
        NotificationSettings d = NotificationSettings.DEFAULTS;
        String lang = m.get("defaultLanguage");
        return new NotificationSettings(
                intOr(m.get("retryMaxAttempts"), d.retryMaxAttempts()),
                intOr(m.get("retryIntervalMinutes"), d.retryIntervalMinutes()),
                boolOr(m.get("refundOnFailure"), d.refundOnFailure()),
                boolOr(m.get("silentHoursEnabled"), d.silentHoursEnabled()),
                timeOr(m.get("silentFrom"), d.silentFrom()),
                timeOr(m.get("silentTo"), d.silentTo()),
                lang != null && LANGUAGES.contains(lang) ? lang : d.defaultLanguage());
    }

    @Transactional
    public NotificationSettings updateNotification(NotificationSettings s, String actor) {
        List<FieldViolation> errors = new ArrayList<>();
        if (s.retryMaxAttempts() < 0 || s.retryMaxAttempts() > 10) {
            errors.add(new FieldViolation("retryMaxAttempts", "error.validation.range",
                    "Giá trị phải từ 0 đến 10", 0, 10));
        }
        if (s.retryIntervalMinutes() < 1 || s.retryIntervalMinutes() > 1440) {
            errors.add(new FieldViolation("retryIntervalMinutes", "error.validation.range",
                    "Giá trị phải từ 1 đến 1440", 1, 1440));
        }
        if (s.silentFrom() == null || !HH_MM.matcher(s.silentFrom()).matches()) {
            errors.add(new FieldViolation("silentFrom", "error.validation.timeFormat", "Giờ phải có dạng HH:mm"));
        }
        if (s.silentTo() == null || !HH_MM.matcher(s.silentTo()).matches()) {
            errors.add(new FieldViolation("silentTo", "error.validation.timeFormat", "Giờ phải có dạng HH:mm"));
        }
        if (s.defaultLanguage() == null || !LANGUAGES.contains(s.defaultLanguage())) {
            errors.add(new FieldViolation("defaultLanguage", "error.validation.invalidValue", "Giá trị không hợp lệ"));
        }
        if (!errors.isEmpty()) {
            throw LaoadsException.validation(errors);
        }
        put("retryMaxAttempts", String.valueOf(s.retryMaxAttempts()), actor);
        put("retryIntervalMinutes", String.valueOf(s.retryIntervalMinutes()), actor);
        put("refundOnFailure", String.valueOf(s.refundOnFailure()), actor);
        put("silentHoursEnabled", String.valueOf(s.silentHoursEnabled()), actor);
        put("silentFrom", s.silentFrom(), actor);
        put("silentTo", s.silentTo(), actor);
        put("defaultLanguage", s.defaultLanguage(), actor);
        log.info("[SETTINGS] {} cập nhật cấu hình gửi tin: {}", actor, s);
        return s;
    }

    private void put(String key, String value, String actor) {
        AppSetting row = settingRepo.findById(PREFIX + key).orElseGet(() -> new AppSetting(PREFIX + key, null));
        row.setValue(value);
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(actor);
        settingRepo.save(row);
    }

    private static int intOr(String v, int def) {
        try {
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolOr(String v, boolean def) {
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static String timeOr(String v, String def) {
        return v != null && HH_MM.matcher(v.trim()).matches() ? v.trim() : def;
    }
}
