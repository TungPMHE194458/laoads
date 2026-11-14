package com.unitel.laoads.domain.auth;

import com.unitel.laoads.domain.user.AppUser;

import java.time.Duration;

/**
 * Port phát access token. Implementation ở laoads-web (JwtUtil) để domain không phụ thuộc thư viện JWT.
 */
public interface TokenIssuer {

    IssuedAccessToken issueAccessToken(AppUser user, Long sessionId);

    Duration refreshTokenTtl();
}
