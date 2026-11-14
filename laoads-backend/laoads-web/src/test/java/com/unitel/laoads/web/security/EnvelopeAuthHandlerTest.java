package com.unitel.laoads.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.web.advice.ErrorResponses;
import com.unitel.laoads.web.config.LocaleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/** 401/403 từ Security filter chain phải ra cùng envelope; token hết hạn → TOKEN_EXPIRED. */
class EnvelopeAuthHandlerTest {

    final ObjectMapper om = new ObjectMapper();
    EnvelopeAuthHandler handler;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        handler = new EnvelopeAuthHandler(om, new ErrorResponses(ms), new LocaleConfig().localeResolver());
    }

    @Test
    void expiredTokenGives401TokenExpired() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR, ErrorCode.TOKEN_EXPIRED);
        req.addHeader("Accept-Language", "en");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.commence(req, res, new InsufficientAuthenticationException("x"));

        JsonNode body = om.readTree(res.getContentAsString());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.has("data")).isTrue();
        assertThat(body.get("statusCode").asText()).isEqualTo("TOKEN_EXPIRED");
        assertThat(body.get("message").asText()).isEqualTo("Access token has expired");
    }

    @Test
    void missingOrInvalidTokenGives401Unauthorized() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.commence(req, res, new InsufficientAuthenticationException("x"));
        JsonNode body = om.readTree(res.getContentAsString());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(body.get("statusCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("Bạn chưa đăng nhập hoặc phiên đăng nhập không hợp lệ");
    }

    @Test
    void accessDeniedGives403InLao() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/users");
        req.addHeader("Accept-Language", "lo");
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.handle(req, res, new AccessDeniedException("x"));
        JsonNode body = om.readTree(res.getContentAsString());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(body.get("statusCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asText()).isEqualTo("ທ່ານບໍ່ມີສິດດຳເນີນການນີ້");
    }
}
