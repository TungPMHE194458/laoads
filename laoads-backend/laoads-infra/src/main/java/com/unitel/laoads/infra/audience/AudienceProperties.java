package com.unitel.laoads.infra.audience;

import com.unitel.laoads.domain.audience.Audience;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình Audience API ({@code laoads.audience.*}). Base URL đã gồm prefix endpoint:
 * OCS {@code …/api/v1/internal}, BCCS3 {@code …/api/v1/internal/bbcs3} (theo tài liệu tích hợp).
 * API key chỉ đặt qua biến môi trường, không lưu DB.
 */
@ConfigurationProperties(prefix = "laoads.audience")
public record AudienceProperties(
        String client,
        String apiKeyHeader,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration streamIdleTimeout,
        Endpoint ocs,
        Endpoint bccs3
) {

    public record Endpoint(String baseUrl, String apiKey) {

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
        }

        public String base() {
            String b = baseUrl == null ? "" : baseUrl.trim();
            return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        }
    }

    public AudienceProperties {
        if (client == null || client.isBlank()) {
            client = "simulated";
        }
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            apiKeyHeader = "x-api-key";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(30);
        }
        if (streamIdleTimeout == null) {
            streamIdleTimeout = Duration.ofSeconds(120);
        }
        if (ocs == null) {
            ocs = new Endpoint(null, null);
        }
        if (bccs3 == null) {
            bccs3 = new Endpoint(null, null);
        }
    }

    public Endpoint endpoint(Audience.Source source) {
        return source == Audience.Source.OCS ? ocs : bccs3;
    }
}
