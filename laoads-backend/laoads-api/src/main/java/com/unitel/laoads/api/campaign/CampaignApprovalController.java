package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.campaign.CampaignDtos.Detail;
import com.unitel.laoads.api.campaign.CampaignDtos.RejectRequest;
import com.unitel.laoads.api.campaign.CampaignDtos.Summary;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.campaign.CampaignEnums;
import com.unitel.laoads.domain.campaign.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phê duyệt chiến dịch (quyền CAMPAIGN_APPROVE). */
@RestController
@RequestMapping(ApiPaths.V1 + "/campaign-approvals")
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "campaign-approval", description = "Phê duyệt chiến dịch")
public class CampaignApprovalController {

    private final CampaignService service;
    private final CampaignViews campaigns;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("@perm.has('CAMPAIGN_APPROVE')")
    @Operation(summary = "Chiến dịch chờ duyệt")
    public ApiResponse<List<Summary>> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String query,
                                           @RequestParam(required = false) CampaignEnums.Mode mode) {
        long ws = workspaceContext.requireWorkspaceId();
        return campaigns.summaries(ws, service.pendingApprovals(ws, query, mode, PageParams.of(page, size)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@perm.has('CAMPAIGN_APPROVE')")
    @Operation(summary = "Duyệt → SCHEDULED (chưa tới giờ) hoặc RUNNING")
    @Auditable(actionType = "APPROVE", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Duyệt chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> approve(@PathVariable Long id) {
        return ApiResponse.ok(campaigns.detail(service.approve(workspaceContext.requireWorkspaceId(), id,
                CurrentUser.require().username())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.has('CAMPAIGN_APPROVE')")
    @Operation(summary = "Từ chối (bắt buộc lý do)")
    @Auditable(actionType = "REJECT", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Từ chối chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest req) {
        return ApiResponse.ok(campaigns.detail(service.reject(workspaceContext.requireWorkspaceId(), id,
                req == null ? null : req.reason(), CurrentUser.require().username())));
    }
}
