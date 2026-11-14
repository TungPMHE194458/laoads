package com.unitel.laoads.api.auth.dto;

import com.unitel.laoads.domain.auth.AuthSession;

/** {@code expiresIn} = số giây sống của access token (1800 = 30 phút). */
public record AuthTokens(String accessToken, String refreshToken, long expiresIn, UserDto user) {

    public static AuthTokens from(AuthSession s) {
        return new AuthTokens(s.accessToken(), s.refreshToken(), s.expiresInSeconds(), UserDto.from(s.user()));
    }
}
