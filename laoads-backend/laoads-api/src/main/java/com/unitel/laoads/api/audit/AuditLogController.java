package com.unitel.laoads.api.audit;

import com.unitel.laoads.api.audit.dto.AuditLogDto;
import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.DateParams;
import com.unitel.laoads.api.common.PageParams;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.api.common.WorkspaceScoped;
import com.unitel.laoads.domain.audit.AuditExcelService;
import com.unitel.laoads.domain.audit.AuditFilter;
import com.unitel.laoads.domain.audit.AuditService;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.Times;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Contract §8 — nhật ký hệ thống.
 * SUPER_ADMIN: xem tất cả (tuỳ chọn lọc {@code workspaceId}); user khác: cần REPORT_VIEW + X-Workspace-Id,
 * chỉ thấy log của workspace đó.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/audit-logs")
@RequiredArgsConstructor
@WorkspaceScoped(required = false)
@Tag(name = "audit-log", description = "Nhật ký hệ thống")
public class AuditLogController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AuditService auditService;
    private final AuditExcelService excelService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or @perm.has('REPORT_VIEW')")
    @Operation(summary = "Tra cứu nhật ký", description = "from/to: yyyy-MM-dd (giờ Lào, to tính hết ngày) "
            + "hoặc ISO-8601. actor tìm 'chứa'. workspaceId chỉ có tác dụng với SUPER_ADMIN.")
    public ApiResponse<List<AuditLogDto>> list(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String actor,
                                               @RequestParam(required = false) String actionType,
                                               @RequestParam(required = false) String entityType,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @Parameter(description = "Chỉ SUPER_ADMIN")
                                               @RequestParam(required = false) Long workspaceId,
                                               @Parameter(description = "SUCCESS | FAILURE")
                                               @RequestParam(required = false) String result) {
        AuditFilter f = filter(actor, actionType, entityType, from, to, workspaceId, result);
        return ApiResponse.page(auditService.search(f, PageParams.of(page, size)), AuditLogDto::from);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @perm.has('REPORT_VIEW')")
    @Operation(summary = "Xuất Excel (.xlsx, không bọc envelope) — cùng bộ lọc, tối đa 10.000 dòng",
            description = "Vượt 10.000 dòng → 400 EXPORT_TOO_LARGE (JSON envelope).")
    @Auditable(actionType = "EXPORT", entityType = "AUDIT_LOG",
            workspaceId = "#currentUser != null && #currentUser.isSuperAdmin() ? null : #workspaceHeader",
            summary = "'Xuất Excel nhật ký hệ thống'")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String actor,
                                         @RequestParam(required = false) String actionType,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) Long workspaceId,
                                         @RequestParam(required = false) String result) {
        AuditFilter f = filter(actor, actionType, entityType, from, to, workspaceId, result);
        byte[] body = excelService.export(f);
        String filename = "audit_log_" + LocalDateTime.now(Times.ZONE).format(FILE_TS) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private AuditFilter filter(String actor, String actionType, String entityType, String from, String to,
                               Long workspaceIdParam, String result) {
        AuthUser user = CurrentUser.require();
        Long ws = user.isSuperAdmin() ? workspaceIdParam : Long.valueOf(workspaceContext.requireWorkspaceId());
        return new AuditFilter(actor, actionType, entityType, ws,
                DateParams.from("from", from), DateParams.to("to", to),
                result == null || result.isBlank() ? null : result.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
