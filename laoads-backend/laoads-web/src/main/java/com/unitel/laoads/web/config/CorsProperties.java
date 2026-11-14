package com.unitel.laoads.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** {@code laoads.cors.*} (copy BOC CorsProperties). */
@ConfigurationProperties(prefix = "laoads.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        Boolean allowCredentials
) {
    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:5173");
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            allowedHeaders = List.of("*");
        }
        if (allowCredentials == null) {
            allowCredentials = true;
        }
    }
}
