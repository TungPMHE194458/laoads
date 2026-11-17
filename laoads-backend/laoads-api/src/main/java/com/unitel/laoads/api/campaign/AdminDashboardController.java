package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.common.ApiPaths;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Dashboard Super Admin (SRS 3.1.1, 3.1.4, 3.1.6, 3.1.7) — toàn hệ thống, không theo workspace. */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "admin-dashboard", description = "Tổng quan Super Admin")
public class AdminDashboardController {

    private final ReportService reports;

    @GetMapping
    @Operation(summary = "Workspace hoạt động, tổng người dùng, chiến dịch, top workspace theo số chiến dịch")
    public ApiResponse<ReportService.AdminDashboard> get(@RequestParam(defaultValue = "7") int periodDays) {
        return ApiResponse.ok(reports.adminDashboard(periodDays));
    }

    @GetMapping("/top-workspaces/export")
    @Operation(summary = "Xuất Excel top workspace theo số chiến dịch")
    @Auditable(actionType = "EXPORT", entityType = "WORKSPACE", summary = "'Xuất Excel top workspace theo số chiến dịch'")
    public ResponseEntity<byte[]> export() {
        // SRS 3.1.7: file xuất khớp đúng dữ liệu đang hiển thị trên Dashboard
        List<ReportService.TopWorkspace> rows = reports.topWorkspaces(ReportService.TOP_WORKSPACES);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Top workspace");
            XSSFCellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            String[] headers = {"STT", "Mã workspace", "Tên workspace", "Tổng chiến dịch", "Đang chạy"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
                h.getCell(i).setCellStyle(bold);
            }
            for (int i = 0; i < rows.size(); i++) {
                ReportService.TopWorkspace w = rows.get(i);
                Row r = sheet.createRow(i + 1);
                r.createCell(0).setCellValue(i + 1d);
                r.createCell(1).setCellValue(w.code());
                r.createCell(2).setCellValue(w.name());
                r.createCell(3).setCellValue(w.campaignCount());
                r.createCell(4).setCellValue(w.runningCount());
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            String file = "top-workspace-" + LocalDate.now() + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(file, StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
