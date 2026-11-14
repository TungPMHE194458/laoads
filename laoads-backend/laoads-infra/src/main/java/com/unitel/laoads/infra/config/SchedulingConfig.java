package com.unitel.laoads.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A+A+ — bật @Scheduled cho DatabaseBootstrap + override Flyway auto-migrate (copy BOC).
 *
 * <p>{@code FlywayMigrationStrategy} mặc định là {@code Flyway::migrate} lúc startup → BE không lên được khi
 * DB down. Override thành no-op; DatabaseBootstrap tự migrate async khi DB sẵn sàng.
 */
@Configuration
@EnableScheduling
@Slf4j
public class SchedulingConfig {

    @Bean
    public FlywayMigrationStrategy lazyFlywayMigrationStrategy() {
        return flyway -> log.info(
                "Flyway auto-migrate skipped at startup — deferred to DatabaseBootstrap scheduler");
    }
}
