package com.unitel.laoads.infra.vsa;

import com.unitel.laoads.domain.auth.vsa.VsaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VsaProperties.class)
@Slf4j
public class VsaConfig {

    @Bean
    public VsaClient vsaClient(VsaProperties props) {
        CasVsaClient client = new CasVsaClient(props);
        log.info("[VSA] enabled={} casUrl={}", client.isEnabled(), props.casUrl());
        return client;
    }
}
