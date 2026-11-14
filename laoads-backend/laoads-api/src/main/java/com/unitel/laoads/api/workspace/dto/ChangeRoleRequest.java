package com.unitel.laoads.api.workspace.dto;

import com.unitel.laoads.domain.workspace.MemberRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull MemberRole role) {
}
