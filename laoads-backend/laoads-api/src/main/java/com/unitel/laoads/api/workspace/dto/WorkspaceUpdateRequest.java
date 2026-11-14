package com.unitel.laoads.api.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /workspaces/{id}: ghi đè toàn bộ — field null/rỗng sẽ xoá giá trị cũ (trừ name bắt buộc). */
public record WorkspaceUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @Size(max = 255) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 32) String contactPhone
) {
}
