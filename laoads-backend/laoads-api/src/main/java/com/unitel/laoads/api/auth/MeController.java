package com.unitel.laoads.api.auth;

import com.unitel.laoads.api.auth.dto.MeResponse;
import com.unitel.laoads.api.auth.dto.MyWorkspace;
import com.unitel.laoads.api.auth.dto.UserDto;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.domain.auth.AuthService;
import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "auth")
public class MeController {

    private final AuthService authService;
    private final WorkspaceService workspaceService;

    @GetMapping(ApiPaths.V1 + "/me")
    @Operation(summary = "User hiện tại + danh sách workspace (kèm role, permissions)")
    public ApiResponse<MeResponse> me() {
        AuthUser actor = CurrentUser.require();
        UserDto user = UserDto.from(authService.currentUser(actor));
        var workspaces = workspaceService.myWorkspaces(actor).stream().map(MyWorkspace::from).toList();
        return ApiResponse.ok(new MeResponse(user, workspaces));
    }
}
