package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.campaign.CampaignDtos.GroupDto;
import com.unitel.laoads.api.campaign.CampaignDtos.GroupRequest;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.campaign.CampaignGroup;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.common.Times;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Nhóm chiến dịch trong workspace. */
@RestController
@RequestMapping(ApiPaths.V1 + "/campaign-groups")
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "campaign-group", description = "Nhóm chiến dịch")
public class CampaignGroupController {

    private final CampaignService service;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Danh sách nhóm (kèm số chiến dịch / đang chạy)")
    public ApiResponse<List<GroupDto>> list() {
        return ApiResponse.ok(service.groups(ws()).stream()
                .map(v -> new GroupDto(v.group().getId(), v.group().getName(), v.group().getDescription(),
                        v.campaignCount(), v.runningCount(),
                        Times.toOffset(v.group().getCreatedAt()))).toList());
    }

    @PostMapping
    @PreAuthorize("@perm.has('CAMPAIGN_CREATE')")
    @Operation(summary = "Tạo nhóm", description = "Trùng tên → 409 GROUP_NAME_DUPLICATE.")
    @Auditable(actionType = "CREATE", entityType = "CAMPAIGN_GROUP", entityId = "#result.id", workspaceId = "#workspaceHeader",
            summary = "'Tạo nhóm chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<GroupDto> create(@Valid @RequestBody GroupRequest req) {
        return ApiResponse.ok(dto(service.createGroup(ws(), req.name(), req.description(), CurrentUser.require().username())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('CAMPAIGN_UPDATE')")
    @Operation(summary = "Sửa tên / mô tả nhóm")
    @Auditable(actionType = "UPDATE", entityType = "CAMPAIGN_GROUP", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Đổi tên nhóm chiến dịch thành \"' + #result.name + '\"'")
    public ApiResponse<GroupDto> rename(@PathVariable Long id, @Valid @RequestBody GroupRequest req) {
        return ApiResponse.ok(dto(service.updateGroup(ws(), id, req.name(), req.description())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('CAMPAIGN_DELETE')")
    @Operation(summary = "Xoá nhóm trống", description = "Còn chiến dịch → 409 GROUP_IN_USE.")
    @Auditable(actionType = "DELETE", entityType = "CAMPAIGN_GROUP", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Xoá nhóm chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<GroupDto> delete(@PathVariable Long id) {
        return ApiResponse.ok(dto(service.deleteGroup(ws(), id)));
    }

    private static GroupDto dto(CampaignGroup g) {
        return new GroupDto(g.getId(), g.getName(), g.getDescription(), 0, 0, Times.toOffset(g.getCreatedAt()));
    }

    private long ws() {
        return workspaceContext.requireWorkspaceId();
    }
}
