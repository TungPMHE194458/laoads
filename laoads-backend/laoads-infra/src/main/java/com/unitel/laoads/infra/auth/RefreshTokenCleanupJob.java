package com.unitel.laoads.infra.auth;

import com.unitel.laoads.domain.auth.RefreshTokenRepository;
import com.unitel.laoads.infra.bootstrap.DatabaseBootstrap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Dọn refresh token đã hết hạn quá 1 ngày — 03:15 hằng ngày giờ Lào. Bỏ qua khi DB chưa sẵn sàng. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository repo;
    private final DatabaseBootstrap bootstrap;

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Vientiane")
    @Transactional
    public void purgeExpired() {
        if (!bootstrap.isMigrated()) {
            return;
        }
        try {
            int n = repo.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
            if (n > 0) {
                log.info("[AUTH] Đã xoá {} refresh token hết hạn", n);
            }
        } catch (Exception e) {
            log.warn("[AUTH] Dọn refresh token thất bại: {}", e.getMessage());
        }
    }
}
