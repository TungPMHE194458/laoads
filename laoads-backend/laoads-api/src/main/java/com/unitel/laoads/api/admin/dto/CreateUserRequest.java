package com.unitel.laoads.api.admin.dto;

import com.unitel.laoads.domain.user.SystemRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code username}: 3–64 ký tự chữ/số/._-, lưu chữ thường. */
public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,64}$") String username,
        @NotBlank @Size(max = 255) String fullName,
        @Email @Size(max = 255) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotNull SystemRole systemRole
) {
}
