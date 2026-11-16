package com.unitel.laoads.domain.segment;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsdnFileParserTest {

    private static final IsdnFileTemplate.Guide GUIDE = new IsdnFileTemplate.Guide("Hướng dẫn", "Hướng dẫn điền file",
            List.of("Mỗi dòng một số thuê bao.", "Ví dụ: 856 20 XXXXXXXX"));

    @Test
    void numericCellsKeepAllDigits() throws IOException {
        // Excel lưu số 13 chữ số dạng số: định dạng General của POI sẽ ra "8.56201E+12" nếu không xử lý riêng
        byte[] file = xlsx(wb -> {
            Sheet s = wb.createSheet("data");
            s.createRow(0).createCell(0).setCellValue("ISDN");
            s.createRow(1).createCell(0).setCellValue(8562055512345d);
            s.createRow(2).createCell(0).setCellValue(2055512346d);
            s.createRow(3).createCell(0).setCellValue("020 5551 2347");
        });

        IsdnFileParser.Result r = IsdnFileParser.parse("ds.xlsx", new ByteArrayInputStream(file));

        assertThat(r.isdns()).containsExactly("8562055512345", "8562055512346", "8562055512347");
        assertThat(r.invalidRows()).isZero();
    }

    @Test
    void readsNamedColumnAndCountsInvalidRows() throws IOException {
        byte[] file = xlsx(wb -> {
            Sheet s = wb.createSheet("data");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Họ tên");
            h.createCell(1).setCellValue("msisdn");
            Row a = s.createRow(1);
            a.createCell(0).setCellValue("A");
            a.createCell(1).setCellValue("8562055512345");
            Row b = s.createRow(2);
            b.createCell(0).setCellValue("B");
            b.createCell(1).setCellValue("abc");
            Row c = s.createRow(3);
            c.createCell(0).setCellValue("C");
            c.createCell(1).setCellValue("+856 20 5551 2345"); // trùng dòng 1
        });

        IsdnFileParser.Result r = IsdnFileParser.parse("ds.xlsx", new ByteArrayInputStream(file));

        assertThat(r.isdns()).containsExactly("8562055512345");
        assertThat(r.invalidRows()).isEqualTo(1);
    }

    @Test
    void csvWithBomAndHeader() {
        byte[] csv = "﻿isdn\n02055512345\n8562055512346\n".getBytes(StandardCharsets.UTF_8);

        IsdnFileParser.Result r = IsdnFileParser.parse("ds.csv", new ByteArrayInputStream(csv));

        assertThat(r.isdns()).containsExactly("8562055512345", "8562055512346");
    }

    @Test
    void blankTemplateIsRejectedAsHavingNoNumbers() {
        byte[] template = IsdnFileTemplate.xlsx(GUIDE);

        assertThatThrownBy(() -> IsdnFileParser.parse("mau.xlsx", new ByteArrayInputStream(template)))
                .isInstanceOf(LaoadsException.class)
                .extracting(e -> ((LaoadsException) e).getCode())
                .isEqualTo(ErrorCode.FILE_INVALID);
    }

    @Test
    void filledTemplateIsReadAndGuideSheetIgnored() throws IOException {
        byte[] filled;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(IsdnFileTemplate.xlsx(GUIDE)));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = wb.getSheetAt(0);
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(data.getRow(0).getCell(0).getStringCellValue()).isEqualTo(IsdnFileTemplate.HEADER);
            assertThat(data.getLastRowNum()).isZero(); // không có dòng ví dụ
            assertThat(data.getColumnStyle(0).getDataFormatString()).isEqualTo("@");
            assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("Hướng dẫn");

            data.createRow(1).createCell(0).setCellValue("2055512345");
            data.createRow(2).createCell(0).setCellValue("8562055512346");
            wb.write(out);
            filled = out.toByteArray();
        }

        IsdnFileParser.Result r = IsdnFileParser.parse("mau.xlsx", new ByteArrayInputStream(filled));

        assertThat(r.isdns()).containsExactly("8562055512345", "8562055512346");
    }

    private interface Fill {
        void into(Workbook wb);
    }

    private static byte[] xlsx(Fill fill) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.into(wb);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
