package com.unitel.laoads.domain.permission;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MemberStatus;
import com.unitel.laoads.domain.workspace.WorkspaceMemberRepository;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionEvaluatorTest {

    @Mock
    WorkspaceMemberRepository memberRepo;
    @Mock
    RolePermissionService rolePermissions;

    PermissionEvaluator evaluator;

    final AuthUser superAdmin = new AuthUser(1L, "superadmin", SystemRole.SUPER_ADMIN, null);
    final AuthUser user = new AuthUser(2L, "member_bsd", SystemRole.USER, null);

    @BeforeEach
    void setUp() {
        evaluator = new PermissionEvaluator(memberRepo, rolePermissions);
    }

    @Test
    void superAdminBypassesWithoutMembership() {
        assertThat(evaluator.hasPermission(superAdmin, 10L, "SECRET_KEY_MANAGE")).isTrue();
        assertThat(evaluator.isActiveMember(superAdmin, 10L)).isTrue();
        verifyNoInteractions(memberRepo, rolePermissions);
    }

    @Test
    void anonymousDenied() {
        assertThat(evaluator.hasPermission(null, 10L, "WORKSPACE_VIEW")).isFalse();
    }

    @Test
    void nonMemberDenied() {
        when(memberRepo.findRole(2L, 10L, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThat(evaluator.hasPermission(user, 10L, "WORKSPACE_VIEW")).isFalse();
        assertThat(evaluator.isActiveMember(user, 10L)).isFalse();
    }

    @Test
    void memberWithPermissionAllowed() {
        when(memberRepo.findRole(2L, 10L, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(MemberRole.MEMBER));
        when(rolePermissions.permissionsOf(MemberRole.MEMBER)).thenReturn(List.of("WORKSPACE_VIEW", "REPORT_VIEW"));
        assertThat(evaluator.hasPermission(user, 10L, "REPORT_VIEW")).isTrue();
    }

    @Test
    void memberWithoutPermissionDenied() {
        when(memberRepo.findRole(anyLong(), anyLong(), any(), any())).thenReturn(Optional.of(MemberRole.MEMBER));
        when(rolePermissions.permissionsOf(MemberRole.MEMBER)).thenReturn(List.of("WORKSPACE_VIEW"));
        assertThat(evaluator.hasPermission(user, 10L, "MEMBER_MANAGE")).isFalse();
    }

    @Test
    void effectivePermissionsForSuperAdminIsFullCatalog() {
        assertThat(evaluator.effectivePermissions(superAdmin, null)).containsExactlyElementsOf(Permission.allCodes());
        assertThat(evaluator.effectivePermissions(user, null)).isEmpty();
    }
}
