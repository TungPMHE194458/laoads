package com.unitel.laoads.domain.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Ghi LADS_AUDIT_LOG async — không block request, lỗi ghi chỉ log warn (vd DB down).
 * Dùng JdbcTemplate (không qua JPA) vì write-only. Thời gian lưu UTC, khớp hibernate.jdbc.time_zone=UTC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditWriter {

    static final int MAX_DETAIL = 8000;
    static final int MAX_SUMMARY = 1000;

    private final JdbcTemplate jdbc;

    @Async
    public void writeAsync(AuditEvent ev) {
        write(ev);
    }

    public void write(AuditEvent ev) {
        try {
            jdbc.update(
                    "INSERT INTO LADS_AUDIT_LOG "
                            + "(ID, ACTOR, ACTOR_ID, ACTION_TYPE, ENTITY_TYPE, ENTITY_ID, WORKSPACE_ID, SUMMARY, "
                            + " DETAIL_JSON, CLIENT_IP, USER_AGENT, REQUEST_ID, CREATED_AT) "
                            + "VALUES (LADS_AUDIT_LOG_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    truncate(ev.actor() == null ? "anonymous" : ev.actor(), 64),
                    ev.actorId(),
                    truncate(ev.actionType(), 32),
                    truncate(ev.entityType(), 64),
                    truncate(ev.entityId(), 64),
                    ev.workspaceId(),
                    truncate(ev.summary(), MAX_SUMMARY),
                    truncate(ev.detailJson(), MAX_DETAIL),
                    truncate(ev.clientIp(), 64),
                    truncate(ev.userAgent(), 500),
                    truncate(ev.requestId(), 64),
                    LocalDateTime.ofInstant(ev.createdAt() == null ? Instant.now() : ev.createdAt(), ZoneOffset.UTC));
        } catch (Exception e) {
            log.warn("[AUDIT] Ghi audit thất bại ({} {}): {}", ev.actionType(), ev.entityType(), e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    public record AuditEvent(
            String actor,
            Long actorId,
            String actionType,
            String entityType,
            String entityId,
            Long workspaceId,
            String summary,
            String detailJson,
            String clientIp,
            String userAgent,
            String requestId,
            Instant createdAt
    ) {
    }
}
