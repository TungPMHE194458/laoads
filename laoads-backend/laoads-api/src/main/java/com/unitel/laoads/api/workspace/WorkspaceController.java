package com.unitel.laoads.api.workspace;

import com.unitel.laoads.api.auth.dto.MyWorkspace;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.workspace.dto.WorkspaceDto;
import com.unitel.laoads.api.workspace.dto.WorkspaceUpdateRequest;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contract §4 — workspace (phía người dùng). */
@RestController
@RequestMapping(ApiPaths.V1 + "/workspaces")
@RequiredArgsConstructor
@Tag(name = "workspace", description = "Workspace của tôi")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping("/my")
    @Operation(summary = "Workspace ACTIVE của tôi (= MeResponse.workspaces)")
    public ApiResponse<List<MyWorkspace>> my() {
        return ApiResponse.ok(workspaceService.myWorkspaces(CurrentUser.require()).stream()
                .map(MyWorkspace::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.isMemberOf(#id)")
    @Operation(summary = "Chi tiết workspace — thành viên ACTIVE hoặc SUPER_ADMIN")
    public ApiResponse<WorkspaceDto> get(@PathVariable Long id) {
        Workspace w = workspaceService.get(id);
        return ApiResponse.ok(WorkspaceDto.from(w, workspaceService.memberCount(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.hasIn(#id, 'WORKSPACE_UPDATE')")
    @Operation(summary = "Cập nhật thông tin workspace — cần WORKSPACE_UPDATE")
    @Auditable(actionType = "UPDATE", entityType = "WORKSPACE", entityId = "#id", workspaceId = "#id",
            summary = "'Cập nhật thông tin workspace ' + #result.code")
    public ApiResponse<WorkspaceDto> update(@PathVariable Long id, @Valid @RequestBody WorkspaceUpdateRequest req) {
        Workspace w = workspaceService.update(id, req.name(), req.description(), req.contactName(),
                req.contactEmail(), req.contactPhone(), CurrentUser.require());
        return ApiResponse.ok(WorkspaceDto.from(w, workspaceService.memberCount(id)));
    }
}
