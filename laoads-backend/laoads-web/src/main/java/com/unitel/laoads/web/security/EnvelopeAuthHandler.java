package com.unitel.laoads.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.web.advice.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Locale;

/**
 * 401/403 phát sinh trong Security filter chain (ngoài DispatcherServlet) → envelope lỗi giống
 * GlobalExceptionHandler. Adapt từ BOC ProblemDetailAuthHandler.
 * Locale tự resolve bằng LocaleResolver vì LocaleContextHolder chưa được DispatcherServlet set ở tầng filter.
 */
@Component
@RequiredArgsConstructor
public class EnvelopeAuthHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ErrorResponses responses;
    private final LocaleResolver localeResolver;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        ErrorCode code = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR) == ErrorCode.TOKEN_EXPIRED
                ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
        write(request, response, code);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        String message = responses.message(code.messageKey(), null, code.name(), locale);
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), responses.body(code, message, null));
    }
}
