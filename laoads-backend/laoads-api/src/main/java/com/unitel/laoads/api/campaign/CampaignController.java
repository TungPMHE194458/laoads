package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.campaign.CampaignDtos.Detail;
import com.unitel.laoads.api.campaign.CampaignDtos.Request;
import com.unitel.laoads.api.campaign.CampaignDtos.Summary;
import com.unitel.laoads.api.campaign.CampaignDtos.TestSendRequest;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignEnums;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.engine.EngineMaintenance;
import com.unitel.laoads.domain.engine.TestSendService;
import com.unitel.laoads.domain.report.ReportService;
import com.unitel.laoads.domain.segment.IsdnFileParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Chiến dịch (SRS 3.2) — theo workspace. */
@RestController
@RequestMapping(ApiPaths.V1 + "/campaigns")
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "campaign", description = "Chiến dịch")
public class CampaignController {

    private final CampaignService service;
    private final ReportService reportService;
    private final TestSendService testSendService;
    private final EngineMaintenance maintenance;
    private final WorkspaceContext workspaceContext;
    private final CampaignViews views;

    @GetMapping
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Danh sách chiến dịch")
    public ApiResponse<List<Summary>> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String query,
                                           @RequestParam(required = false) CampaignEnums.Status status,
                                           @RequestParam(required = false) CampaignEnums.Mode mode,
                                           @RequestParam(required = false) Long groupId) {
        long ws = ws();
        Page<Campaign> p = service.list(ws, query, status, mode, groupId, PageParams.of(page, size));
        return summaries(ws, p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('CAMPAIGN_VIEW')")
    @Operation(summary = "Chi tiết chiến dịch (cấu hình + lịch sử)")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.ok(detail(service.get(ws(), id)));
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("@perm.has('REPORT_VIEW')")
    @Operation(summary = "Báo cáo hiệu quả: tổng, chuỗi thời gian (DAY/WEEK/MONTH), theo kênh, theo nhóm A/B")
    public ApiResponse<ReportService.CampaignReport> report(@PathVariable Long id,
                                                            @RequestParam(required = false) String bucket) {
        return ApiResponse.ok(reportService.campaignReport(ws(), id, bucket));
    }

    @PostMapping
    @PreAuthorize("@perm.has('CAMPAIGN_CREATE') and (!#submit or @perm.has('CAMPAIGN_SUBMIT'))")
    @Operation(summary = "Tạo chiến dịch (submit=true: gửi duyệt luôn)")
    @Auditable(actionType = "CREATE", actionTypeExpr = "#submit ? 'SUBMIT' : 'CREATE'", entityType = "CAMPAIGN",
            entityId = "#result.id", workspaceId = "#workspaceHeader",
            summary = "(#submit ? 'Tạo & gửi duyệt' : 'Tạo nháp') + ' chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> create(@Valid @RequestBody Request req,
                                      @RequestParam(defaultValue = "false") boolean submit) {
        return ApiResponse.ok(detail(service.create(ws(), req.toInput(), submit, user())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('CAMPAIGN_UPDATE') and (!#submit or @perm.has('CAMPAIGN_SUBMIT'))")
    @Operation(summary = "Sửa chiến dịch Nháp / Bị từ chối", description = "Trạng thái khác → 409 CAMPAIGN_LOCKED.")
    @Auditable(actionType = "UPDATE", actionTypeExpr = "#submit ? 'SUBMIT' : 'UPDATE'", entityType = "CAMPAIGN",
            entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "(#submit ? 'Sửa & gửi duyệt' : 'Cập nhật') + ' chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> update(@PathVariable Long id, @Valid @RequestBody Request req,
                                      @RequestParam(defaultValue = "false") boolean submit) {
        return ApiResponse.ok(detail(service.update(ws(), id, req.toInput(), submit, user())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('CAMPAIGN_DELETE')")
    @Operation(summary = "Xoá chiến dịch Nháp / Bị từ chối / Đã huỷ")
    @Auditable(actionType = "DELETE", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Xoá chiến dịch id=' + #id")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(ws(), id, user());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.has('CAMPAIGN_SUBMIT')")
    @Operation(summary = "Gửi duyệt")
    @Auditable(actionType = "SUBMIT", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Gửi duyệt \"' + #result.name + '\"'")
    public ApiResponse<Detail> submit(@PathVariable Long id) {
        return ApiResponse.ok(detail(service.submit(ws(), id, user())));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("@perm.has('CAMPAIGN_UPDATE')")
    @Operation(summary = "Tạm dừng")
    @Auditable(actionType = "PAUSE", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Tạm dừng chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> pause(@PathVariable Long id) {
        return ApiResponse.ok(detail(service.pause(ws(), id, user())));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("@perm.has('CAMPAIGN_UPDATE')")
    @Operation(summary = "Tiếp tục")
    @Auditable(actionType = "RESUME", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Tiếp tục chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> resume(@PathVariable Long id) {
        return ApiResponse.ok(detail(service.resume(ws(), id, user())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.has('CAMPAIGN_UPDATE')")
    @Operation(summary = "Huỷ (huỷ luôn các tin chưa gửi)")
    @Auditable(actionType = "CANCEL", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Huỷ chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> cancel(@PathVariable Long id) {
        return ApiResponse.ok(detail(service.cancel(ws(), id, user())));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("@perm.has('CAMPAIGN_CREATE')")
    @Operation(summary = "Nhân bản thành chiến dịch Nháp")
    @Auditable(actionType = "CREATE", entityType = "CAMPAIGN", entityId = "#result.id", workspaceId = "#workspaceHeader",
            summary = "'Nhân bản chiến dịch \"' + #result.name + '\"'")
    public ApiResponse<Detail> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(detail(service.duplicate(ws(), id, user())));
    }

    @PostMapping("/test-send")
    @PreAuthorize("@perm.has('CAMPAIGN_CREATE') or @perm.has('CAMPAIGN_UPDATE')")
    @Operation(summary = "Bắn tin thử tới danh sách ISDN (tối đa 20 số)")
    public ApiResponse<List<TestSendService.Result>> testSend(@RequestBody TestSendRequest req) {
        return ApiResponse.ok(testSendService.send(ws(), req.campaignId(), req.isdns(), req.actions(), user()));
    }

    @PostMapping(value = "/{id}/conversions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.has('CAMPAIGN_UPDATE')")
    @Operation(summary = "Mục tiêu MANUAL_FILE: tải danh sách số thuê bao đã chuyển đổi")
    @Auditable(actionType = "IMPORT", entityType = "CAMPAIGN", entityId = "#id", workspaceId = "#workspaceHeader",
            summary = "'Import danh sách chuyển đổi: ' + #result.get('matched') + ' thuê bao'")
    public ApiResponse<Map<String, Integer>> importConversions(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        service.get(ws(), id);
        try (InputStream in = file.getInputStream()) {
            IsdnFileParser.Result parsed = IsdnFileParser.parse(file.getOriginalFilename(), in);
            int matched = maintenance.importConversions(id, parsed.isdns());
            return ApiResponse.ok(Map.of("matched", matched, "rows", parsed.isdns().size(), "invalidRows", parsed.invalidRows()));
        } catch (IOException e) {
            throw new LaoadsException(ErrorCode.FILE_INVALID, "File không hợp lệ", "không đọc được file");
        }
    }

    // ───────────── tiện ích ─────────────

    private ApiResponse<List<Summary>> summaries(long ws, Page<Campaign> p) {
        return views.summaries(ws, p);
    }

    private Detail detail(Campaign c) {
        return views.detail(c);
    }

    private long ws() {
        return workspaceContext.requireWorkspaceId();
    }

    private static String user() {
        return CurrentUser.require().username();
    }
}
