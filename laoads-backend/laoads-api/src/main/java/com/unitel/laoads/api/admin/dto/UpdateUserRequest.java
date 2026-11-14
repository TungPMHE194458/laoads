package com.unitel.laoads.api.admin.dto;

import com.unitel.laoads.domain.user.SystemRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ghi đè toàn bộ: email/phone null → xoá giá trị cũ. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Email @Size(max = 255) String email,
        @Size(max = 32) String phone,
        @NotNull SystemRole systemRole
) {
}
