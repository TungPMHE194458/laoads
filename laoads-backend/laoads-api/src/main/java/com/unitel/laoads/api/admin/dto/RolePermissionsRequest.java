package com.unitel.laoads.api.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Danh sách permission code mới của role (thay toàn bộ). Code lạ → 400 VALIDATION_ERROR. */
public record RolePermissionsRequest(@NotNull List<@NotNull String> permissions) {
}
