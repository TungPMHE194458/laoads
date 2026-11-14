package com.unitel.laoads.api.auth;

import com.unitel.laoads.api.auth.dto.AuthTokens;
import com.unitel.laoads.api.auth.dto.ChangePasswordRequest;
import com.unitel.laoads.api.auth.dto.LoginRequest;
import com.unitel.laoads.api.auth.dto.LogoutRequest;
import com.unitel.laoads.api.auth.dto.RefreshRequest;
import com.unitel.laoads.api.auth.dto.VsaConfigDto;
import com.unitel.laoads.api.auth.dto.VsaLoginRequest;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.ClientInfos;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.auth.AuthService;
import com.unitel.laoads.domain.auth.vsa.VsaClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract §3 — Auth. */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "Đăng nhập / refresh / VSA / đổi mật khẩu")
public class AuthController {

    private final AuthService authService;
    private final VsaClient vsaClient;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập bằng username/mật khẩu",
            description = "Sai thông tin → 401 UNAUTHORIZED; tài khoản bị khoá → 403 USER_LOCKED.")
    @Auditable(actionType = "LOGIN", entityType = "USER",
            actor = "#result.user.username", actorId = "#result.user.id", entityId = "#result.user.id",
            summary = "'Đăng nhập bằng mật khẩu'")
    public ApiResponse<AuthTokens> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(AuthTokens.from(authService.login(req.username(), req.password(),
                ClientInfos.from(http))));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Xoay vòng refresh token",
            description = "Refresh token cũ bị thu hồi. Token sai/hết hạn/đã dùng → 401 UNAUTHORIZED (FE đăng xuất).")
    public ApiResponse<AuthTokens> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return ApiResponse.ok(AuthTokens.from(authService.refresh(req.refreshToken(), ClientInfos.from(http))));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Đăng xuất — thu hồi refresh token (idempotent)")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req.refreshToken());
        return ApiResponse.ok();
    }

    @GetMapping("/vsa/config")
    @SecurityRequirements
    @Operation(summary = "Cấu hình đăng nhập VSA cho FE")
    public ApiResponse<VsaConfigDto> vsaConfig() {
        return ApiResponse.ok(new VsaConfigDto(vsaClient.isEnabled(), vsaClient.loginUrl()));
    }

    @PostMapping("/vsa/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập bằng ticket VSA (CAS 2.0)",
            description = "VSA tắt → 400 VSA_DISABLED; user VSA chưa có tài khoản ACTIVE → 403 VSA_USER_NOT_PROVISIONED.")
    @Auditable(actionType = "LOGIN", entityType = "USER",
            actor = "#result.user.username", actorId = "#result.user.id", entityId = "#result.user.id",
            summary = "'Đăng nhập qua VSA'")
    public ApiResponse<AuthTokens> vsaLogin(@Valid @RequestBody VsaLoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(AuthTokens.from(authService.vsaLogin(req.ticket(), req.service(),
                ClientInfos.from(http))));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Tự đổi mật khẩu",
            description = "Sai mật khẩu cũ → 400 WRONG_PASSWORD (không phải 401). Các phiên khác bị thu hồi.")
    @Auditable(actionType = "CHANGE_PASSWORD", entityType = "USER", entityId = "#currentUser.id",
            summary = "'Đổi mật khẩu'")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(CurrentUser.require(), req.oldPassword(), req.newPassword());
        return ApiResponse.ok();
    }
}
