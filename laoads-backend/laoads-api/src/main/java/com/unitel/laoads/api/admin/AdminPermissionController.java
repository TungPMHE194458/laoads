package com.unitel.laoads.api.admin;

import com.unitel.laoads.api.admin.dto.PermissionDef;
import com.unitel.laoads.api.admin.dto.RoleDef;
import com.unitel.laoads.api.admin.dto.RolePermissionsRequest;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.permission.Permission;
import com.unitel.laoads.domain.permission.RolePermissionService;
import com.unitel.laoads.domain.workspace.MemberRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Contract §6 — danh mục permission + ma trận role → permission (SUPER_ADMIN). */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "admin-permission", description = "Ma trận quyền (SUPER_ADMIN)")
public class AdminPermissionController {

    private final RolePermissionService rolePermissionService;
    private final MessageSource messages;

    @GetMapping("/permissions")
    @Operation(summary = "Danh mục permission (mô tả dịch theo Accept-Language)")
    public ApiResponse<List<PermissionDef>> permissions() {
        return ApiResponse.ok(Arrays.stream(Permission.values())
                .map(p -> new PermissionDef(p.name(), p.group(), msg(p.messageKey(), p.defaultDescription())))
                .toList());
    }

    @GetMapping("/roles")
    @Operation(summary = "Vai trò workspace + permission hiện hành")
    public ApiResponse<List<RoleDef>> roles() {
        return ApiResponse.ok(Arrays.stream(MemberRole.values()).map(this::toRoleDef).toList());
    }

    @PutMapping("/roles/{code}/permissions")
    @Operation(summary = "Thay toàn bộ permission của 1 vai trò", description = "Permission lạ → 400 VALIDATION_ERROR. "
            + "Evict cache auth.role-permissions.")
    @Auditable(actionType = "UPDATE", entityType = "ROLE_PERMISSION", entityId = "#code.name()",
            summary = "'Cập nhật quyền vai trò ' + #code.name() + ': ' + #req.permissions")
    public ApiResponse<RoleDef> updateRole(@PathVariable MemberRole code,
                                           @Valid @RequestBody RolePermissionsRequest req) {
        List<String> perms = rolePermissionService.replace(code, req.permissions(), CurrentUser.require().username());
        return ApiResponse.ok(new RoleDef(code, msg(code.messageKey(), code.defaultName()), perms));
    }

    private RoleDef toRoleDef(MemberRole role) {
        return new RoleDef(role, msg(role.messageKey(), role.defaultName()), rolePermissionService.permissionsOf(role));
    }

    private String msg(String key, String fallback) {
        return messages.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
    }
}
