package com.unitel.laoads.web.security;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.auth.IssuedAccessToken;
import com.unitel.laoads.domain.auth.TokenIssuer;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.SystemRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Ký / parse access token (HS256/384/512 tuỳ độ dài secret). Claims: {@code sub}=username, {@code uid},
 * {@code sr}=systemRole, {@code sid}=id refresh token phát sinh phiên. Adapt từ BOC JwtUtil.
 */
@Component
public class JwtUtil implements TokenIssuer {

    static final String CLAIM_UID = "uid";
    static final String CLAIM_ROLE = "sr";
    static final String CLAIM_SESSION = "sid";
    static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        String secret = props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("laoads.jwt.secret (env LAOADS_JWT_SECRET) chưa set hoặc < "
                    + MIN_SECRET_BYTES + " byte (256 bit)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IssuedAccessToken issueAccessToken(AppUser user, Long sessionId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getUsername())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_ROLE, user.getSystemRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(key);
        if (sessionId != null) {
            builder.claim(CLAIM_SESSION, sessionId);
        }
        return new IssuedAccessToken(builder.compact(), props.accessTtl().toSeconds());
    }

    @Override
    public Duration refreshTokenTtl() {
        return props.refreshTtl();
    }

    /** @throws io.jsonwebtoken.ExpiredJwtException khi hết hạn; JwtException khi sai chữ ký/định dạng. */
    public AuthUser parse(String token) throws JwtException {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number uid = c.get(CLAIM_UID, Number.class);
        Number sid = c.get(CLAIM_SESSION, Number.class);
        String role = c.get(CLAIM_ROLE, String.class);
        if (uid == null || role == null) {
            throw new io.jsonwebtoken.MalformedJwtException("Thiếu claim uid/sr");
        }
        SystemRole systemRole;
        try {
            systemRole = SystemRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new io.jsonwebtoken.MalformedJwtException("Claim sr không hợp lệ: " + role);
        }
        return new AuthUser(uid.longValue(), c.getSubject(), systemRole, sid == null ? null : sid.longValue());
    }
}
