package com.unitel.laoads.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A+A+ — async Flyway bootstrap (copy pattern từ BOC).
 *
 * <p>BE start được dù DB down. Scheduler ping DB mỗi 30s; khi DB sẵn sàng tự gọi {@code flyway.migrate()}
 * đúng 1 lần rồi phát {@link DatabaseReadyEvent}. Hikari {@code initialization-fail-timeout: -1} cho pool
 * start dù chưa mở được connection; Flyway KHÔNG migrate lúc start (FlywayMigrationStrategy no-op ở
 * SchedulingConfig).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseBootstrap {

    private final DataSource dataSource;
    private final Flyway flyway;
    private final Environment env;
    private final ApplicationEventPublisher events;

    private final AtomicBoolean migrated = new AtomicBoolean(false);
    private final AtomicInteger attempts = new AtomicInteger(0);
    private volatile String lastError;

    private boolean isDevProfile() {
        return Arrays.asList(env.getActiveProfiles()).contains("dev");
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void tryMigrate() {
        if (migrated.get()) {
            return;
        }
        int n = attempts.incrementAndGet();
        try (Connection c = dataSource.getConnection()) {
            if (!c.isValid(3)) {
                lastError = "Connection invalid (isValid=false)";
                log.warn("DB connection invalid (attempt {}). Retry in 30s.", n);
                return;
            }
            log.info("DB connection OK (attempt {}). Running Flyway migrations (history LADS_FLYWAY_HISTORY)…", n);
            if (isDevProfile()) {
                // Dev: dọn failed entries + đồng bộ checksum khi V* file thay đổi.
                // UAT/Prod KHÔNG repair — repair có thể che lỗi migration thật.
                flyway.repair();
            }
            var result = flyway.migrate();
            migrated.set(true);
            lastError = null;
            log.info("Flyway migrations completed: {} migrations applied, schema at version {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
            publishReady(result.migrationsExecuted, result.targetSchemaVersion);
        } catch (SQLException e) {
            lastError = e.getMessage();
            log.warn("DB still unavailable (attempt {}): {} — retry in 30s", n, e.getMessage());
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("Flyway migration failed (attempt {}): {} — retry in 30s", n, e.getMessage(), e);
        }
    }

    private void publishReady(int executed, String version) {
        try {
            events.publishEvent(new DatabaseReadyEvent(executed, version));
        } catch (Exception e) {
            log.warn("[A++] DatabaseReadyEvent listener lỗi: {}", e.getMessage());
        }
    }

    public boolean isMigrated() {
        return migrated.get();
    }

    public int getAttempts() {
        return attempts.get();
    }

    public String getLastError() {
        return lastError;
    }
}
