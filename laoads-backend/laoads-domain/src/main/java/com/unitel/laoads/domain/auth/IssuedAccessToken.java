package com.unitel.laoads.domain.auth;

/** Access token đã ký + thời hạn (giây) để trả {@code expiresIn}. */
public record IssuedAccessToken(String token, long expiresInSeconds) {
}
