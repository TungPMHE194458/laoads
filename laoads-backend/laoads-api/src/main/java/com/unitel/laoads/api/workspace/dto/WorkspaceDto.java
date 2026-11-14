package com.unitel.laoads.api.workspace.dto;

import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;

import java.time.OffsetDateTime;

/** {@code memberCount} = số thành viên ACTIVE. */
public record WorkspaceDto(
        Long id,
        String code,
        String name,
        String description,
        WorkspaceStatus status,
        String contactName,
        String contactEmail,
        String contactPhone,
        long memberCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WorkspaceDto from(Workspace w, long memberCount) {
        return new WorkspaceDto(w.getId(), w.getCode(), w.getName(), w.getDescription(), w.getStatus(),
                w.getContactName(), w.getContactEmail(), w.getContactPhone(), memberCount,
                Times.toOffset(w.getCreatedAt()), Times.toOffset(w.getUpdatedAt()));
    }
}
