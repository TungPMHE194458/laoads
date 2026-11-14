package com.unitel.laoads.domain.auth;

import com.unitel.laoads.domain.user.AppUser;

/** Kết quả login/refresh: access token + refresh token raw (chỉ trả cho client đúng 1 lần) + user. */
public record AuthSession(String accessToken, String refreshToken, long expiresInSeconds, AppUser user) {
}
