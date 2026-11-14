package com.unitel.laoads.api.workspace;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.workspace.dto.WorkspaceCreateRequest;
import com.unitel.laoads.api.workspace.dto.WorkspaceDto;
import com.unitel.laoads.api.workspace.dto.WorkspaceStatusRequest;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceService;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Contract §4 — quản trị workspace (SUPER_ADMIN). */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/workspaces")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "admin-workspace", description = "Quản trị workspace (SUPER_ADMIN)")
public class AdminWorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    @Operation(summary = "Danh sách workspace (tìm theo code/tên), sắp xếp createdAt giảm dần")
    public ApiResponse<List<WorkspaceDto>> list(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String query,
                                                @RequestParam(required = false) WorkspaceStatus status) {
        Page<Workspace> result = workspaceService.adminSearch(query, status, PageParams.of(page, size));
        Map<Long, Long> counts = workspaceService.memberCounts(
                result.getContent().stream().map(Workspace::getId).toList());
        return ApiResponse.page(result, w -> WorkspaceDto.from(w, counts.getOrDefault(w.getId(), 0L)));
    }

    @PostMapping
    @Operation(summary = "Tạo workspace + gán OWNER", description = "Trùng code → 409 WORKSPACE_CODE_DUPLICATE; "
            + "ownerUsername không tồn tại → 404 USER_NOT_FOUND.")
    @Auditable(actionType = "CREATE", entityType = "WORKSPACE", workspaceId = "#result.id",
            summary = "'Tạo workspace ' + #result.code + ' (OWNER: ' + #req.ownerUsername + ')'")
    public ApiResponse<WorkspaceDto> create(@Valid @RequestBody WorkspaceCreateRequest req) {
        Workspace w = workspaceService.create(req.code(), req.name(), req.description(), req.ownerUsername(),
                CurrentUser.require());
        return ApiResponse.ok(WorkspaceDto.from(w, workspaceService.memberCount(w.getId())));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khoá / mở khoá workspace")
    @Auditable(actionType = "UPDATE", entityType = "WORKSPACE",
            actionTypeExpr = "#req.status.name() == 'LOCKED' ? 'LOCK' : 'UNLOCK'",
            entityId = "#id", workspaceId = "#id",
            summary = "(#req.status.name() == 'LOCKED' ? 'Khoá' : 'Mở khoá') + ' workspace ' + #result.code")
    public ApiResponse<WorkspaceDto> changeStatus(@PathVariable Long id,
                                                  @Valid @RequestBody WorkspaceStatusRequest req) {
        Workspace w = workspaceService.changeStatus(id, req.status(), CurrentUser.require());
        return ApiResponse.ok(WorkspaceDto.from(w, workspaceService.memberCount(id)));
    }
}
