package com.unitel.laoads.domain.segment;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * File mẫu import tệp khách hàng (SRS 3.5.1 "Tải file mẫu").
 * <ul>
 *   <li>Sheet 1 — chỉ có tiêu đề {@value #HEADER}; cột A định dạng Text để Excel không đổi số dài thành 8.56E+12.
 *       Không có dòng ví dụ: người dùng điền tiếp ngay dưới tiêu đề, ví dụ lẫn vào sẽ thành số nhận tin thật.</li>
 *   <li>Sheet 2 — hướng dẫn (đã dịch theo ngôn ngữ người dùng). {@link IsdnFileParser} chỉ đọc sheet đầu tiên.</li>
 * </ul>
 */
public final class IsdnFileTemplate {

    public static final String HEADER = "isdn";
    public static final String DATA_SHEET = "ISDN";

    /** Nội dung sheet hướng dẫn. */
    public record Guide(String sheetName, String title, List<String> lines) {
    }

    private IsdnFileTemplate() {
    }

    public static byte[] xlsx(Guide guide) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            CellStyle header = wb.createCellStyle();
            header.cloneStyleFrom(text);
            header.setFont(bold);

            Sheet data = wb.createSheet(DATA_SHEET);
            data.setDefaultColumnStyle(0, text);
            data.setColumnWidth(0, 24 * 256);
            Cell h = data.createRow(0).createCell(0);
            h.setCellValue(HEADER);
            h.setCellStyle(header);
            data.createFreezePane(0, 1);

            Sheet help = wb.createSheet(WorkbookUtil.createSafeSheetName(guide.sheetName()));
            help.setColumnWidth(0, 110 * 256);
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(bold);
            Cell title = help.createRow(0).createCell(0);
            title.setCellValue(guide.title());
            title.setCellStyle(titleStyle);
            for (int i = 0; i < guide.lines().size(); i++) {
                help.createRow(i + 2).createCell(0).setCellValue((i + 1) + ". " + guide.lines().get(i));
            }

            wb.setActiveSheet(0);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
