package com.unitel.laoads.api.audit.dto;

import com.unitel.laoads.domain.audit.AuditLog;
import com.unitel.laoads.domain.common.Times;

import java.time.OffsetDateTime;

public record AuditLogDto(
        Long id,
        String actor,
        String actionType,
        String entityType,
        String entityId,
        String summary,
        String detailJson,
        Long workspaceId,
        String ip,
        OffsetDateTime createdAt
) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActor(), a.getActionType(), a.getEntityType(), a.getEntityId(),
                a.getSummary(), a.getDetailJson(), a.getWorkspaceId(), a.getClientIp(),
                Times.toOffset(a.getCreatedAt()));
    }
}
