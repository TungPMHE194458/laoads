package com.unitel.laoads.api.workspace.dto;

import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MemberStatus;
import com.unitel.laoads.domain.workspace.WorkspaceMember;

import java.time.OffsetDateTime;

public record MemberDto(
        Long id,
        Long userId,
        String username,
        String fullName,
        String email,
        String phone,
        MemberRole role,
        MemberStatus status,
        OffsetDateTime joinedAt
) {
    public static MemberDto from(WorkspaceMember m) {
        AppUser u = m.getUser();
        return new MemberDto(m.getId(), u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                m.getRole(), m.getStatus(), Times.toOffset(m.getJoinedAt()));
    }
}
