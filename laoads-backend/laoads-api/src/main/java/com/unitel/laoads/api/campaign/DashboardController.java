package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dashboard của workspace (SRS 3.1) — số liệu thật từ engine. */
@RestController
@RequestMapping(ApiPaths.V1 + "/dashboard")
@RequiredArgsConstructor
@WorkspaceScoped
@Tag(name = "dashboard", description = "Tổng quan workspace")
public class DashboardController {

    private final ReportService reports;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/summary")
    @PreAuthorize("@perm.has('REPORT_VIEW')")
    @Operation(summary = "Chỉ số tổng quan (đang chạy, chờ duyệt, tổng chiến dịch, khách hàng tiếp cận)")
    public ApiResponse<ReportService.DashboardSummary> summary(@RequestParam(defaultValue = "7") int periodDays) {
        return ApiResponse.ok(reports.summary(workspaceContext.requireWorkspaceId(), periodDays));
    }

    @GetMapping("/trend")
    @PreAuthorize("@perm.has('REPORT_VIEW')")
    @Operation(summary = "Xu hướng gửi / mở / click / chuyển đổi theo ngày")
    public ApiResponse<List<ReportService.TimePoint>> trend(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(reports.trend(workspaceContext.requireWorkspaceId(), days));
    }

    @GetMapping("/channels")
    @PreAuthorize("@perm.has('REPORT_VIEW')")
    @Operation(summary = "Hiệu quả theo kênh")
    public ApiResponse<List<ReportService.ChannelStat>> channels(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(reports.channels(workspaceContext.requireWorkspaceId(), days));
    }
}
