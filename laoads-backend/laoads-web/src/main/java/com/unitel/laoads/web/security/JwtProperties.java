package com.unitel.laoads.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code laoads.jwt.*}. Secret PHẢI ≥ 32 byte (256 bit) — lấy từ env {@code LAOADS_JWT_SECRET};
 * chỉ application-dev.yml có default dev.
 */
@ConfigurationProperties(prefix = "laoads.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl,
        String issuer
) {
    public JwtProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofMinutes(30);
        }
        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(7);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "laoads-unitel";
        }
    }
}
