package com.unitel.laoads.domain.auth;

import com.unitel.laoads.domain.user.SystemRole;

import java.security.Principal;

/**
 * Principal đã xác thực (dựng từ access token JWT). {@code sessionId} = id của refresh token
 * đã sinh ra access token này (claim {@code sid}) — dùng để giữ phiên hiện tại khi đổi mật khẩu.
 */
public record AuthUser(Long id, String username, SystemRole systemRole, Long sessionId) implements Principal {

    public boolean isSuperAdmin() {
        return systemRole == SystemRole.SUPER_ADMIN;
    }

    @Override
    public String getName() {
        return username;
    }
}
