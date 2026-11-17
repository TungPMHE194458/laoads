package com.unitel.laoads.domain.audit;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Times;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Export Excel nhật ký (adapt từ BOC AuditExcelService). Giới hạn {@link #MAX_ROWS} dòng/lần để tránh OOM;
 * vượt → 400 EXPORT_TOO_LARGE (yêu cầu thu hẹp bộ lọc). Tiêu đề cột dịch theo Accept-Language.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditExcelService {

    public static final int MAX_ROWS = 10_000;

    private static final String[][] HEADERS = {
            {"audit.export.time", "Thời gian"},
            {"audit.export.actor", "Người thực hiện"},
            {"audit.export.action", "Hành động"},
            {"audit.export.entityType", "Đối tượng"},
            {"audit.export.entityId", "ID đối tượng"},
            {"audit.export.workspace", "Workspace"},
            {"audit.export.summary", "Tóm tắt"},
            {"audit.export.ip", "IP"},
            {"audit.export.detail", "Chi tiết"},
            {"audit.export.result", "Kết quả"},
            {"audit.export.errorCode", "Mã lỗi"}
    };

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EXCEL_CELL_MAX = 32_000;

    private final AuditService auditService;
    private final MessageSource messages;

    @Transactional(readOnly = true)
    public byte[] export(AuditFilter filter) {
        Page<AuditLog> probe = auditService.search(filter, PageRequest.of(0, MAX_ROWS + 1));
        long total = probe.getTotalElements();
        if (total > MAX_ROWS) {
            throw new LaoadsException(ErrorCode.EXPORT_TOO_LARGE,
                    "Quá nhiều bản ghi (" + total + ", tối đa " + MAX_ROWS + ") — vui lòng thu hẹp bộ lọc",
                    total, MAX_ROWS);
        }
        List<AuditLog> rows = probe.getContent();
        Locale locale = LocaleContextHolder.getLocale();

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet(messages.getMessage("audit.export.sheet", null, "Nhật ký", locale));
            writeHeader(wb, sheet, locale);
            int r = 1;
            for (AuditLog a : rows) {
                Row out = sheet.createRow(r++);
                out.createCell(0).setCellValue(formatTs(a.getCreatedAt()));
                out.createCell(1).setCellValue(nullSafe(a.getActor()));
                out.createCell(2).setCellValue(nullSafe(a.getActionType()));
                out.createCell(3).setCellValue(nullSafe(a.getEntityType()));
                out.createCell(4).setCellValue(nullSafe(a.getEntityId()));
                out.createCell(5).setCellValue(a.getWorkspaceId() == null ? "" : String.valueOf(a.getWorkspaceId()));
                out.createCell(6).setCellValue(nullSafe(a.getSummary()));
                out.createCell(7).setCellValue(nullSafe(a.getClientIp()));
                String detail = nullSafe(a.getDetailJson());
                out.createCell(8).setCellValue(detail.length() > EXCEL_CELL_MAX
                        ? detail.substring(0, EXCEL_CELL_MAX) : detail);
                out.createCell(9).setCellValue(messages.getMessage("audit.result." + nullSafe(a.getResult()), null,
                        nullSafe(a.getResult()), locale));
                out.createCell(10).setCellValue(nullSafe(a.getErrorCode()));
            }
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                wb.write(bos);
                return bos.toByteArray();
            }
        } catch (IOException e) {
            throw new LaoadsException(ErrorCode.INTERNAL_ERROR, "Không xuất được file Excel: " + e.getMessage());
        }
    }

    private void writeHeader(Workbook wb, Sheet sheet, Locale locale) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Row row = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(messages.getMessage(HEADERS[i][0], null, HEADERS[i][1], locale));
            c.setCellStyle(style);
        }
    }

    private static String formatTs(Instant t) {
        if (t == null) {
            return "";
        }
        return LocalDateTime.ofInstant(t, Times.ZONE).format(TS_FORMAT);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
