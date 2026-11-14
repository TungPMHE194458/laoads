package com.unitel.laoads.api.admin;

import com.unitel.laoads.api.admin.dto.CreateUserRequest;
import com.unitel.laoads.api.admin.dto.ResetPasswordRequest;
import com.unitel.laoads.api.admin.dto.UpdateUserRequest;
import com.unitel.laoads.api.admin.dto.UserStatusRequest;
import com.unitel.laoads.api.auth.dto.UserDto;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserService;
import com.unitel.laoads.domain.user.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contract §6 — quản trị user (SUPER_ADMIN). */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "admin-user", description = "Quản trị tài khoản (SUPER_ADMIN)")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách user — query tìm username/tên/email/SĐT, sắp xếp createdAt giảm dần")
    public ApiResponse<List<UserDto>> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String query,
                                           @RequestParam(required = false) UserStatus status,
                                           @RequestParam(required = false) SystemRole systemRole) {
        return ApiResponse.page(userService.search(query, status, systemRole, PageParams.of(page, size)),
                UserDto::from);
    }

    @PostMapping
    @Operation(summary = "Tạo user", description = "Trùng username → 409 USERNAME_DUPLICATE.")
    @Auditable(actionType = "CREATE", entityType = "USER",
            summary = "'Tạo tài khoản ' + #result.username + ' (' + #result.systemRole + ')'")
    public ApiResponse<UserDto> create(@Valid @RequestBody CreateUserRequest req) {
        return ApiResponse.ok(UserDto.from(userService.create(req.username(), req.fullName(), req.email(),
                req.phone(), req.password(), req.systemRole(), CurrentUser.require())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật user", description = "Tự đổi systemRole của mình → 400 CANNOT_CHANGE_SELF.")
    @Auditable(actionType = "UPDATE", entityType = "USER", entityId = "#id",
            summary = "'Cập nhật tài khoản ' + #result.username")
    public ApiResponse<UserDto> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.ok(UserDto.from(userService.update(id, req.fullName(), req.email(), req.phone(),
                req.systemRole(), CurrentUser.require())));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khoá / mở khoá user", description = "Tự khoá mình → 400 CANNOT_LOCK_SELF. "
            + "Khoá → thu hồi mọi refresh token.")
    @Auditable(actionType = "UPDATE", entityType = "USER",
            actionTypeExpr = "#req.status.name() == 'LOCKED' ? 'LOCK' : 'UNLOCK'", entityId = "#id",
            summary = "(#req.status.name() == 'LOCKED' ? 'Khoá' : 'Mở khoá') + ' tài khoản ' + #result.username")
    public ApiResponse<UserDto> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest req) {
        return ApiResponse.ok(UserDto.from(userService.changeStatus(id, req.status(), CurrentUser.require())));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Đặt lại mật khẩu (admin nhập tay) — thu hồi mọi refresh token của user")
    @Auditable(actionType = "RESET_PASSWORD", entityType = "USER", entityId = "#id",
            summary = "'Đặt lại mật khẩu cho user id=' + #id")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.newPassword(), CurrentUser.require());
        return ApiResponse.ok();
    }
}
