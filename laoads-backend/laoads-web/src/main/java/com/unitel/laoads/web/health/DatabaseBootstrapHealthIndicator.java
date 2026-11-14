package com.unitel.laoads.web.health;

import com.unitel.laoads.infra.bootstrap.DatabaseBootstrap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * {@code /actuator/health} component {@code flywayBootstrap} (copy BOC):
 * migrated=true → UP; chưa migrate (DB down / đang chờ) → OUT_OF_SERVICE.
 */
@Component("flywayBootstrap")
@RequiredArgsConstructor
public class DatabaseBootstrapHealthIndicator implements HealthIndicator {

    private final DatabaseBootstrap bootstrap;

    @Override
    public Health health() {
        if (bootstrap.isMigrated()) {
            return Health.up()
                    .withDetail("migrated", true)
                    .withDetail("attempts", bootstrap.getAttempts())
                    .build();
        }
        Health.Builder b = Health.outOfService()
                .withDetail("migrated", false)
                .withDetail("attempts", bootstrap.getAttempts())
                .withDetail("message", "Waiting for DB / pending Flyway migrations");
        if (bootstrap.getLastError() != null) {
            b.withDetail("lastError", bootstrap.getLastError());
        }
        return b.build();
    }
}
