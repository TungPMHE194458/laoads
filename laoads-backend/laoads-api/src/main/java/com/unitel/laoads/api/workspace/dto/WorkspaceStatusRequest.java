package com.unitel.laoads.api.workspace.dto;

import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import jakarta.validation.constraints.NotNull;

public record WorkspaceStatusRequest(@NotNull WorkspaceStatus status) {
}
