package com.unitel.laoads.domain.permission;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MemberStatus;
import com.unitel.laoads.domain.workspace.WorkspaceMemberRepository;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Logic kiểm tra quyền trong workspace (contract §2):
 * <ul>
 *   <li>SUPER_ADMIN → luôn true, kể cả không là thành viên.</li>
 *   <li>Còn lại: phải là thành viên ACTIVE của workspace ACTIVE, và role có permission trong ma trận.</li>
 * </ul>
 * Không tự đọc header — {@code @perm} (laoads-api) truyền workspaceId vào.
 */
@Service
@RequiredArgsConstructor
public class PermissionEvaluator {

    private final WorkspaceMemberRepository memberRepo;
    private final RolePermissionService rolePermissions;

    @Transactional(readOnly = true)
    public boolean hasPermission(AuthUser user, long workspaceId, String permission) {
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        return activeRole(user.id(), workspaceId)
                .map(role -> rolePermissions.permissionsOf(role).contains(permission))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(AuthUser user, long workspaceId) {
        if (user == null) {
            return false;
        }
        return user.isSuperAdmin() || activeRole(user.id(), workspaceId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<MemberRole> activeRole(Long userId, long workspaceId) {
        if (userId == null) {
            return Optional.empty();
        }
        return memberRepo.findRole(userId, workspaceId, MemberStatus.ACTIVE, WorkspaceStatus.ACTIVE);
    }

    /** Permission hiệu lực cho /me: SUPER_ADMIN = toàn bộ danh mục. */
    public List<String> effectivePermissions(AuthUser user, MemberRole role) {
        if (user != null && user.isSuperAdmin()) {
            return Permission.allCodes();
        }
        return role == null ? List.of() : rolePermissions.permissionsOf(role);
    }
}
