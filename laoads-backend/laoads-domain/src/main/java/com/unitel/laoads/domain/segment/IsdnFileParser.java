package com.unitel.laoads.domain.segment;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.Isdns;
import com.unitel.laoads.domain.common.LaoadsException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Đọc danh sách số thuê bao từ file .xlsx / .xls / .csv (SRS 3.5.1). Cột số thuê bao: cột có tiêu đề
 * isdn / msisdn / phone / sdt / so_thue_bao, nếu không có tiêu đề thì lấy cột đầu tiên. Bỏ trùng, bỏ dòng sai.
 */
public final class IsdnFileParser {

    public record Result(Set<String> isdns, int invalidRows) {
    }

    public static final int MAX_ROWS = 2_000_000;
    private static final Set<String> HEADERS = Set.of("isdn", "msisdn", "phone", "sdt", "so_thue_bao", "số thuê bao",
            "phone_number", "mobile");

    private IsdnFileParser() {
    }

    public static Result parse(String fileName, InputStream in) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return parseCsv(in);
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return parseExcel(in);
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof LaoadsException le) {
                throw le;
            }
            throw new LaoadsException(ErrorCode.FILE_INVALID, "File không hợp lệ", "không đọc được nội dung");
        }
        throw new LaoadsException(ErrorCode.FILE_INVALID, "File không hợp lệ", "chỉ nhận .xlsx, .xls, .csv");
    }

    private static Result parseCsv(InputStream in) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        int invalid = 0;
        int col = 0;
        boolean first = true;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.replace("﻿", "").split("[,;\\t]");
                if (first) {
                    first = false;
                    int h = headerIndex(cells);
                    if (h >= 0) {
                        col = h;
                        continue;
                    }
                }
                invalid += add(out, col < cells.length ? cells[col] : null);
                guard(out.size() + invalid);
            }
        }
        return finish(out, invalid);
    }

    private static Result parseExcel(InputStream in) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        int invalid = 0;
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int col = 0;
            boolean first = true;
            for (Row row : sheet) {
                if (first) {
                    first = false;
                    String[] cells = new String[Math.max(0, row.getLastCellNum())];
                    for (int i = 0; i < cells.length; i++) {
                        Cell c = row.getCell(i);
                        cells[i] = c == null ? "" : fmt.formatCellValue(c);
                    }
                    int h = headerIndex(cells);
                    if (h >= 0) {
                        col = h;
                        continue;
                    }
                }
                Cell c = row.getCell(col);
                String v = c == null ? null : fmt.formatCellValue(c);
                if (v == null || v.isBlank()) {
                    continue;
                }
                invalid += add(out, v);
                guard(out.size() + invalid);
            }
        }
        return finish(out, invalid);
    }

    private static int headerIndex(String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null && HEADERS.contains(cells[i].trim().toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    private static int add(Set<String> out, String raw) {
        var n = Isdns.normalize(raw);
        if (n.isPresent()) {
            out.add(n.get());
            return 0;
        }
        return 1;
    }

    private static void guard(int rows) {
        if (rows > MAX_ROWS) {
            throw new LaoadsException(ErrorCode.FILE_INVALID, "File không hợp lệ", "quá " + MAX_ROWS + " dòng");
        }
    }

    private static Result finish(Set<String> out, int invalid) {
        if (out.isEmpty()) {
            throw new LaoadsException(ErrorCode.FILE_INVALID, "File không hợp lệ", "không có số thuê bao hợp lệ");
        }
        return new Result(out, invalid);
    }
}
