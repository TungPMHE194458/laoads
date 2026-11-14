package com.unitel.laoads.api.workspace.dto;

import com.unitel.laoads.domain.workspace.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code identifier} = username, email hoặc SĐT của user đã có tài khoản. */
public record AddMemberRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotNull MemberRole role
) {
}
