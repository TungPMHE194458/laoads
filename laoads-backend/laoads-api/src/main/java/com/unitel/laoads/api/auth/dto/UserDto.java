package com.unitel.laoads.api.auth.dto;

import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserStatus;

import java.time.OffsetDateTime;

public record UserDto(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        SystemRole systemRole,
        UserStatus status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt
) {
    public static UserDto from(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getSystemRole(), u.getStatus(), Times.toOffset(u.getLastLoginAt()), Times.toOffset(u.getCreatedAt()));
    }
}
