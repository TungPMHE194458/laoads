package com.unitel.laoads.infra.vsa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code laoads.vsa.*} — SSO VSA (CAS 2.0).
 *
 * @param enabled        bật/tắt (mặc định false)
 * @param casUrl         gốc CAS, vd {@code https://vsa.example.la/cas}
 * @param serviceUrl     URL FE nhận ticket sau khi login CAS, vd {@code https://laoads.example.la/auth/vsa/callback}
 * @param connectTimeout timeout mở kết nối tới CAS
 * @param readTimeout    timeout chờ phản hồi serviceValidate
 */
@ConfigurationProperties(prefix = "laoads.vsa")
public record VsaProperties(
        boolean enabled,
        String casUrl,
        String serviceUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public VsaProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
