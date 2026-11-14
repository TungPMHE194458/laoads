package com.unitel.laoads.web.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.audit.AuditWriter;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** SpEL trên record (#result.user.username, #req.status.name()), che secret trong detailJson. */
public class AuditAspectTest {

    final AuditAspect aspect = new AuditAspect(mock(AuditWriter.class), new ObjectMapper().findAndRegisterModules());

    public record Login(String username, String password) {
    }

    public record UserView(Long id, String username) {
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn, UserView user) {
    }

    public record StatusReq(WorkspaceStatus status) {
    }

    public record Ws(Long id, String code) {
    }

    public static class FakeController {
        @Auditable(actionType = "LOGIN", entityType = "USER", actor = "#result.user.username",
                actorId = "#result.user.id", entityId = "#result.user.id", summary = "'Đăng nhập bằng mật khẩu'")
        public ApiResponse<Tokens> login(Login req) {
            return null;
        }

        @Auditable(actionType = "UPDATE", entityType = "WORKSPACE",
                actionTypeExpr = "#req.status.name() == 'LOCKED' ? 'LOCK' : 'UNLOCK'",
                entityId = "#id", workspaceId = "#id",
                summary = "(#req.status.name() == 'LOCKED' ? 'Khoá' : 'Mở khoá') + ' workspace ' + #result.code")
        public ApiResponse<Ws> status(Long id, StatusReq req) {
            return null;
        }

        @Auditable(actionType = "EXPORT", entityType = "AUDIT_LOG",
                workspaceId = "#currentUser != null && #currentUser.isSuperAdmin() ? null : #workspaceHeader")
        public ApiResponse<Ws> export(String actor) {
            return null;
        }
    }

    private static Method method(String name) {
        for (Method m : FakeController.class.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private AuditWriter.AuditEvent build(String name, Object[] args, Object result, AuthUser current, Long wsHeader) {
        Method m = method(name);
        return aspect.buildEvent(m, args, m.getAnnotation(Auditable.class), result, current, wsHeader,
                "10.0.0.1", "JUnit", "req-1");
    }

    @Test
    void loginResolvesActorFromResultAndMasksSecrets() {
        var result = ApiResponse.ok(new Tokens("access.jwt.value", "refresh-raw", 1800,
                new UserView(5L, "owner_bsd")));
        AuditWriter.AuditEvent ev = build("login", new Object[]{new Login("Owner_BSD", "s3cret!")}, result, null, null);

        assertThat(ev.actionType()).isEqualTo("LOGIN");
        assertThat(ev.actor()).isEqualTo("owner_bsd");
        assertThat(ev.actorId()).isEqualTo(5L);
        assertThat(ev.entityId()).isEqualTo("5");
        assertThat(ev.summary()).isEqualTo("Đăng nhập bằng mật khẩu");
        assertThat(ev.clientIp()).isEqualTo("10.0.0.1");
        assertThat(ev.detailJson())
                .contains("\"password\":\"***\"", "\"accessToken\":\"***\"", "\"refreshToken\":\"***\"")
                .doesNotContain("s3cret!", "access.jwt.value", "refresh-raw");
    }

    @Test
    void statusChangeUsesExpressionsAndCurrentUser() {
        AuthUser sa = new AuthUser(1L, "superadmin", SystemRole.SUPER_ADMIN, 3L);
        AuditWriter.AuditEvent ev = build("status", new Object[]{7L, new StatusReq(WorkspaceStatus.LOCKED)},
                ApiResponse.ok(new Ws(7L, "BSD")), sa, null);

        assertThat(ev.actionType()).isEqualTo("LOCK");
        assertThat(ev.actor()).isEqualTo("superadmin");
        assertThat(ev.actorId()).isEqualTo(1L);
        assertThat(ev.entityId()).isEqualTo("7");
        assertThat(ev.workspaceId()).isEqualTo(7L);
        assertThat(ev.summary()).isEqualTo("Khoá workspace BSD");

        AuditWriter.AuditEvent unlock = build("status", new Object[]{7L, new StatusReq(WorkspaceStatus.ACTIVE)},
                ApiResponse.ok(new Ws(7L, "BSD")), sa, null);
        assertThat(unlock.actionType()).isEqualTo("UNLOCK");
    }

    @Test
    void workspaceHeaderVariableAndDefaultEntityId() {
        AuthUser owner = new AuthUser(2L, "owner_bsd", SystemRole.USER, null);
        AuditWriter.AuditEvent ev = build("export", new Object[]{"adm"}, ApiResponse.ok(new Ws(9L, "X")), owner, 10L);
        assertThat(ev.workspaceId()).isEqualTo(10L);
        assertThat(ev.entityId()).isEqualTo("9");

        AuthUser sa = new AuthUser(1L, "superadmin", SystemRole.SUPER_ADMIN, null);
        assertThat(build("export", new Object[]{"adm"}, ApiResponse.ok(null), sa, 10L).workspaceId()).isNull();
    }
}
