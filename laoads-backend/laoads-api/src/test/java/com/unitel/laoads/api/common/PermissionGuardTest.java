package com.unitel.laoads.api.common;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.permission.PermissionEvaluator;
import com.unitel.laoads.domain.permission.RolePermissionService;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MemberStatus;
import com.unitel.laoads.domain.workspace.WorkspaceMemberRepository;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@code @perm} + PermissionEvaluator thật trên repo mock: header, SUPER_ADMIN bypass, membership, ma trận quyền. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionGuardTest {

    @Mock
    WorkspaceMemberRepository memberRepo;
    @Mock
    RolePermissionService rolePermissions;

    WorkspaceContext ctx;
    PermissionGuard perm;

    @BeforeEach
    void setUp() {
        ctx = new WorkspaceContext();
        perm = new PermissionGuard(ctx, new PermissionEvaluator(memberRepo, rolePermissions));
        when(rolePermissions.permissionsOf(MemberRole.MEMBER)).thenReturn(List.of("WORKSPACE_VIEW", "REPORT_VIEW"));
        when(rolePermissions.permissionsOf(MemberRole.ADMIN))
                .thenReturn(List.of("WORKSPACE_VIEW", "WORKSPACE_UPDATE", "MEMBER_MANAGE", "REPORT_VIEW"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Thiếu X-Workspace-Id → 400 WORKSPACE_REQUIRED (trước cả kiểm tra SUPER_ADMIN)")
    void missingHeader() {
        login(1L, SystemRole.SUPER_ADMIN);
        ctx.init(null);
        assertCode(() -> perm.has("WORKSPACE_VIEW"), ErrorCode.WORKSPACE_REQUIRED);
    }

    @Test
    void nonNumericHeaderIsInvalidParam() {
        login(2L, SystemRole.USER);
        ctx.init("abc");
        assertCode(() -> perm.has("WORKSPACE_VIEW"), ErrorCode.INVALID_PARAM);
    }

    @Test
    void superAdminBypassesMembership() {
        login(1L, SystemRole.SUPER_ADMIN);
        ctx.init("10");
        assertThat(perm.has("SECRET_KEY_MANAGE")).isTrue();
        assertThat(perm.superAdmin()).isTrue();
        verifyNoInteractions(memberRepo);
    }

    @Test
    void nonMemberDenied() {
        login(2L, SystemRole.USER);
        ctx.init("10");
        when(memberRepo.findRole(2L, 10L, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThat(perm.has("WORKSPACE_VIEW")).isFalse();
        assertThat(perm.isMemberOf(10L)).isFalse();
    }

    @Test
    void memberPermissionPresentAndAbsent() {
        login(2L, SystemRole.USER);
        ctx.init("10");
        when(memberRepo.findRole(2L, 10L, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(MemberRole.MEMBER));
        assertThat(perm.has("REPORT_VIEW")).isTrue();
        assertThat(perm.has("MEMBER_MANAGE")).isFalse();
    }

    @Test
    @DisplayName("/workspaces/{id}/members: path id phải trùng header")
    void pathMustMatchHeader() {
        login(3L, SystemRole.USER);
        ctx.init("10");
        when(memberRepo.findRole(3L, 10L, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(MemberRole.ADMIN));
        assertThat(perm.hasForPath(10L, "MEMBER_MANAGE")).isTrue();
        assertCode(() -> perm.hasForPath(11L, "MEMBER_MANAGE"), ErrorCode.INVALID_PARAM);
    }

    @Test
    @DisplayName("/workspaces/{id}: theo path id, không cần header")
    void hasInIgnoresHeader() {
        login(3L, SystemRole.USER);
        ctx.init(null);
        when(memberRepo.findRole(3L, 20L, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(MemberRole.ADMIN));
        assertThat(perm.hasIn(20L, "WORKSPACE_UPDATE")).isTrue();
        assertThat(perm.isMemberOf(20L)).isTrue();
    }

    @Test
    void anonymousDenied() {
        ctx.init("10");
        assertThat(perm.has("WORKSPACE_VIEW")).isFalse();
        assertThat(perm.superAdmin()).isFalse();
    }

    private static void login(long id, SystemRole role) {
        AuthUser u = new AuthUser(id, "u" + id, role, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(LaoadsException.class)
                .extracting(e -> ((LaoadsException) e).getCode())
                .isEqualTo(code);
    }
}
