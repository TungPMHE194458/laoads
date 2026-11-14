package com.unitel.laoads.web.security;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.auth.IssuedAccessToken;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.SystemRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static JwtUtil jwt(Duration accessTtl) {
        return new JwtUtil(new JwtProperties(SECRET, accessTtl, Duration.ofDays(7), "laoads-unitel"));
    }

    private static AppUser user() {
        AppUser u = new AppUser();
        u.setId(42L);
        u.setUsername("owner_bsd");
        u.setSystemRole(SystemRole.USER);
        return u;
    }

    @Test
    void roundTripCarriesUserRoleAndSession() {
        JwtUtil util = jwt(Duration.ofMinutes(30));
        IssuedAccessToken t = util.issueAccessToken(user(), 77L);
        assertThat(t.expiresInSeconds()).isEqualTo(1800);
        AuthUser a = util.parse(t.token());
        assertThat(a.id()).isEqualTo(42L);
        assertThat(a.username()).isEqualTo("owner_bsd");
        assertThat(a.systemRole()).isEqualTo(SystemRole.USER);
        assertThat(a.sessionId()).isEqualTo(77L);
        assertThat(util.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void expiredTokenThrowsExpiredJwtException() {
        JwtUtil util = jwt(Duration.ofMinutes(-5));
        String token = util.issueAccessToken(user(), 1L).token();
        assertThatThrownBy(() -> util.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenRejected() {
        JwtUtil util = jwt(Duration.ofMinutes(30));
        String token = util.issueAccessToken(user(), 1L).token();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThatThrownBy(() -> util.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void weakSecretRefused() {
        assertThatThrownBy(() -> new JwtUtil(new JwtProperties("short", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filterMarksExpiredTokenAndLeavesContextEmpty() throws Exception {
        JwtUtil expired = jwt(Duration.ofMinutes(-5));
        String token = expired.issueAccessToken(user(), 1L).token();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt(Duration.ofMinutes(30)));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(req.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR)).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void filterAuthenticatesValidToken() throws Exception {
        JwtUtil util = jwt(Duration.ofMinutes(30));
        String token = util.issueAccessToken(user(), 9L).token();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(util);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                assertThat(auth.getPrincipal()).isInstanceOf(AuthUser.class);
                assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
            }
        };
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(req.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR)).isNull();
    }
}
