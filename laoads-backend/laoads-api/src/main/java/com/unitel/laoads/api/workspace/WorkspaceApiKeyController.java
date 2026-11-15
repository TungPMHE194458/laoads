package com.unitel.laoads.api.workspace;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.engine.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** API key của workspace cho API sự kiện — quyền SECRET_KEY_MANAGE. Key đầy đủ chỉ trả về khi tạo. */
@RestController
@RequestMapping(ApiPaths.V1 + "/workspaces/{id}/api-key")
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "workspace-api-key", description = "API key sự kiện")
public class WorkspaceApiKeyController {

    public record KeyDto(boolean configured, String hint, OffsetDateTime rotatedAt, String key) {
    }

    private final ApiKeyService service;

    @GetMapping
    @PreAuthorize("@perm.hasForPath(#id, 'SECRET_KEY_MANAGE')")
    @Operation(summary = "Trạng thái API key (chỉ 4 ký tự cuối)")
    public ApiResponse<KeyDto> get(@PathVariable Long id) {
        ApiKeyService.KeyInfo k = service.info(id);
        return ApiResponse.ok(new KeyDto(k.configured(), k.hint(), Times.toOffset(k.rotatedAt()), null));
    }

    @PostMapping
    @PreAuthorize("@perm.hasForPath(#id, 'SECRET_KEY_MANAGE')")
    @Operation(summary = "Tạo / xoay vòng API key (key cũ hết hiệu lực ngay) — trả key đầy đủ đúng 1 lần")
    @Auditable(actionType = "UPDATE", entityType = "API_KEY", entityId = "#id", workspaceId = "#id",
            summary = "'Tạo mới API key sự kiện'")
    public ApiResponse<KeyDto> rotate(@PathVariable Long id) {
        String key = service.rotate(id, CurrentUser.require().username());
        ApiKeyService.KeyInfo k = service.info(id);
        return ApiResponse.ok(new KeyDto(true, k.hint(), Times.toOffset(k.rotatedAt()), key));
    }

    @DeleteMapping
    @PreAuthorize("@perm.hasForPath(#id, 'SECRET_KEY_MANAGE')")
    @Operation(summary = "Thu hồi API key")
    @Auditable(actionType = "DELETE", entityType = "API_KEY", entityId = "#id", workspaceId = "#id",
            summary = "'Thu hồi API key sự kiện'")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        service.revoke(id, CurrentUser.require().username());
        return ApiResponse.ok();
    }
}
