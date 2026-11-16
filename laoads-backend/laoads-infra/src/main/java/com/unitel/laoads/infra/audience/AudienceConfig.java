package com.unitel.laoads.infra.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.domain.audience.AudienceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Audience API: {@code laoads.audience.client=http} → {@link HttpAudienceClient} (gọi OCS / BCCS3 thật);
 * mặc định {@code simulated} → {@code SimulatedAudienceClient} (laoads-infra simulation).
 */
@Configuration
@EnableConfigurationProperties(AudienceProperties.class)
@Slf4j
public class AudienceConfig {

    @Bean
    @ConditionalOnProperty(name = "laoads.audience.client", havingValue = "http")
    public AudienceClient httpAudienceClient(AudienceProperties props, ObjectMapper mapper) {
        log.info("[AUDIENCE] client=http OCS={} ({}), BCCS3={} ({})",
                props.ocs().base(), props.ocs().configured() ? "đủ cấu hình" : "THIẾU URL/API key",
                props.bccs3().base(), props.bccs3().configured() ? "đủ cấu hình" : "THIẾU URL/API key");
        return new HttpAudienceClient(props, mapper);
    }
}
