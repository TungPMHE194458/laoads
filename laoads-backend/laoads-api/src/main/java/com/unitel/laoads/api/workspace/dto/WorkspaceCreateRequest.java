package com.unitel.laoads.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code code}: 2–32 ký tự chữ/số/_/-, lưu chữ HOA. */
public record WorkspaceCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$") String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 64) String ownerUsername
) {
}
