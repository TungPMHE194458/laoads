package com.unitel.laoads.web.security;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Đọc {@code Authorization: Bearer <jwt>} → principal {@link AuthUser}, authority {@code ROLE_<systemRole>}.
 * Token hết hạn → đánh dấu request {@link #AUTH_ERROR_ATTR}=TOKEN_EXPIRED để EnvelopeAuthHandler trả
 * 401 TOKEN_EXPIRED (FE sẽ gọi /auth/refresh); token sai → 401 UNAUTHORIZED. Endpoint permitAll vẫn đi qua
 * bình thường dù token hỏng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTR = "laoads.auth.error";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MDC_USER_ID = "userId";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean userIdSet = false;
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AuthUser user = jwtUtil.parse(token);
                MDC.put(MDC_USER_ID, String.valueOf(user.id()));
                userIdSet = true;
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.systemRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                request.setAttribute(AUTH_ERROR_ATTR, ErrorCode.TOKEN_EXPIRED);
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("JWT parse failed: {}", e.getMessage());
                request.setAttribute(AUTH_ERROR_ATTR, ErrorCode.UNAUTHORIZED);
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (userIdSet) {
                MDC.remove(MDC_USER_ID);
            }
        }
    }
}
