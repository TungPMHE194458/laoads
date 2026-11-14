package com.unitel.laoads.web.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fail-fast cho profile uat/prod (adapt BOC StartupValidator) — chặn app start nếu:
 * <ul>
 *   <li>JWT secret thiếu / còn dev default (DEV-ONLY-…) / &lt; 32 byte;</li>
 *   <li>DB url/username/password (cả 2 pool) rỗng;</li>
 *   <li>prod: CORS allowed-origins rỗng;</li>
 *   <li>VSA bật nhưng thiếu cas-url/service-url.</li>
 * </ul>
 * Không đụng DB (BE vẫn phải start được khi DB down).
 */
@Component
@Profile({"uat", "prod"})
@Slf4j
public class StartupValidator {

    private final Environment env;

    @Value("${laoads.jwt.secret:}")
    private String jwtSecret;

    public StartupValidator(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void validate() {
        String profiles = String.join(",", env.getActiveProfiles());
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        List<String> errors = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            errors.add("LAOADS_JWT_SECRET chưa set (env var)");
        } else if (jwtSecret.startsWith("DEV-ONLY-")) {
            errors.add("LAOADS_JWT_SECRET vẫn là dev default — KHÔNG được dùng ở " + profiles);
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            errors.add("LAOADS_JWT_SECRET quá ngắn (< 32 byte / 256 bit)");
        }

        requireProp(errors, "spring.datasource.url", "LAOADS_DB_URL (hoặc BOC_DB_URL)");
        requireProp(errors, "spring.datasource.username", "LAOADS_DB_USERNAME (hoặc BOC_DB_USERNAME)");
        requireProp(errors, "spring.datasource.password", "LAOADS_DB_PASSWORD (hoặc BOC_DB_PASSWORD)");
        requireProp(errors, "laoads.read-only-datasource.jdbc-url", "LAOADS_DB_URL (pool read-only)");
        requireProp(errors, "laoads.read-only-datasource.password", "LAOADS_DB_PASSWORD (pool read-only)");

        // application-prod.yml khai báo dạng scalar ${LAOADS_CORS_ALLOWED_ORIGINS:} → chỉ kiểm key scalar
        // (không xét [0] vì application.yml có list mặc định localhost).
        if (prod && blank(env.getProperty("laoads.cors.allowed-origins"))) {
            errors.add("LAOADS_CORS_ALLOWED_ORIGINS chưa set — prod KHÔNG được mở wildcard");
        }

        if (Boolean.parseBoolean(env.getProperty("laoads.vsa.enabled", "false"))) {
            requireProp(errors, "laoads.vsa.cas-url", "LAOADS_VSA_CAS_URL");
            requireProp(errors, "laoads.vsa.service-url", "LAOADS_VSA_SERVICE_URL");
        }

        if (!errors.isEmpty()) {
            log.error("=== STARTUP VALIDATION FAILED ({}) ===", profiles);
            errors.forEach(e -> log.error("  - {}", e));
            throw new IllegalStateException("Config không hợp lệ cho profile " + profiles + ": "
                    + String.join("; ", errors));
        }
        log.info("[STARTUP] {} config validated OK", profiles);
    }

    private void requireProp(List<String> errors, String key, String hint) {
        if (blank(env.getProperty(key))) {
            errors.add(hint + " chưa set (" + key + ")");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
