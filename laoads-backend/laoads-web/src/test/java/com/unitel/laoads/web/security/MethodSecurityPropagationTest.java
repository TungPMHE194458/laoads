package com.unitel.laoads.web.security;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bảo đảm cơ chế: LaoadsException ném từ bean {@code @perm} bên trong {@code @PreAuthorize} xuyên qua SpEL
 * nguyên vẹn (→ GlobalExceptionHandler trả 400 WORKSPACE_REQUIRED), còn {@code false} → AccessDeniedException
 * (→ 403). Context Spring tối thiểu, không DB.
 */
@SpringJUnitConfig(MethodSecurityPropagationTest.Config.class)
class MethodSecurityPropagationTest {

    @Autowired
    Guarded guarded;

    @Test
    @WithMockUser
    void domainExceptionFromPermBeanPropagates() {
        assertThatThrownBy(() -> guarded.missingHeader())
                .isInstanceOfSatisfying(LaoadsException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.WORKSPACE_REQUIRED));
    }

    @Test
    @WithMockUser
    void falseBecomesAccessDenied() {
        assertThatThrownBy(() -> guarded.denied()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser
    void trueProceeds() {
        assertThat(guarded.allowed(5L)).isEqualTo("ok-5");
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean("perm")
        PermStub perm() {
            return new PermStub();
        }

        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    public static class PermStub {
        public boolean has(String permission) {
            if ("NO_HEADER".equals(permission)) {
                throw new LaoadsException(ErrorCode.WORKSPACE_REQUIRED, "Thiếu header X-Workspace-Id");
            }
            return "OK".equals(permission);
        }

        public boolean hasForPath(Long id, String permission) {
            return id != null && has(permission);
        }
    }

    public static class Guarded {
        @PreAuthorize("@perm.has('NO_HEADER')")
        public void missingHeader() {
            // không bao giờ chạy tới
        }

        @PreAuthorize("@perm.has('DENY')")
        public void denied() {
            // không bao giờ chạy tới
        }

        @PreAuthorize("@perm.hasForPath(#id, 'OK')")
        public String allowed(Long id) {
            return "ok-" + id;
        }
    }
}
