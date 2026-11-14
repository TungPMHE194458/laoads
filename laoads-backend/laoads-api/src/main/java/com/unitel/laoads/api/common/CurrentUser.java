package com.unitel.laoads.api.common;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Lấy {@link AuthUser} (principal do JwtAuthenticationFilter đặt) từ SecurityContext. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser orNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser u) {
            return u;
        }
        return null;
    }

    public static AuthUser require() {
        AuthUser u = orNull();
        if (u == null) {
            throw new LaoadsException(ErrorCode.UNAUTHORIZED, "Bạn chưa đăng nhập");
        }
        return u;
    }
}
