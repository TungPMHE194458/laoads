package com.unitel.laoads.domain.audit;

import java.time.Instant;

/**
 * Bộ lọc nhật ký. {@code from} inclusive, {@code to} exclusive. {@code workspaceId} null = tất cả
 * (chỉ SUPER_ADMIN; user thường luôn bị ép theo X-Workspace-Id).
 */
public record AuditFilter(String actor, String actionType, String entityType, Long workspaceId,
                          Instant from, Instant to) {
}
