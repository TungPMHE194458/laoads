package com.unitel.laoads.api.common;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.permission.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Bean {@code @perm} cho SpEL: {@code @PreAuthorize("@perm.has('MEMBER_MANAGE')")}.
 *
 * <ul>
 *   <li>{@link #has(String)} — endpoint 🏢: workspace lấy từ X-Workspace-Id (thiếu → 400 WORKSPACE_REQUIRED).</li>
 *   <li>{@link #hasForPath(Long, String)} — {@code /workspaces/{id}/members/**}: path id phải trùng header.</li>
 *   <li>{@link #hasIn(Long, String)} / {@link #isMemberOf(Long)} — {@code /workspaces/{id}}: workspace theo path,
 *       không cần header.</li>
 * </ul>
 * Trả false → Spring Security ném AccessDeniedException → 403 FORBIDDEN. Exception nghiệp vụ (400) ném trực
 * tiếp từ đây xuyên qua SpEL tới GlobalExceptionHandler (xem MethodSecurityPropagationTest).
 */
@Component("perm")
@RequiredArgsConstructor
public class PermissionGuard {

    private final WorkspaceContext workspaceContext;
    private final PermissionEvaluator evaluator;

    public boolean has(String permission) {
        long workspaceId = workspaceContext.requireWorkspaceId();
        return evaluator.hasPermission(CurrentUser.orNull(), workspaceId, permission);
    }

    public boolean hasForPath(Long pathWorkspaceId, String permission) {
        long workspaceId = workspaceContext.requireWorkspaceId();
        if (!Objects.equals(pathWorkspaceId, workspaceId)) {
            throw LaoadsException.withKey(ErrorCode.INVALID_PARAM, "error.INVALID_PARAM.workspaceMismatch",
                    "X-Workspace-Id không khớp với workspace trong đường dẫn");
        }
        return evaluator.hasPermission(CurrentUser.orNull(), workspaceId, permission);
    }

    public boolean hasIn(Long workspaceId, String permission) {
        return workspaceId != null && evaluator.hasPermission(CurrentUser.orNull(), workspaceId, permission);
    }

    public boolean isMemberOf(Long workspaceId) {
        return workspaceId != null && evaluator.isActiveMember(CurrentUser.orNull(), workspaceId);
    }

    public boolean superAdmin() {
        AuthUser u = CurrentUser.orNull();
        return u != null && u.isSuperAdmin();
    }
}
