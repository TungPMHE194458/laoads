package com.unitel.laoads.domain.audience;

import com.unitel.laoads.domain.audience.Audience.Condition;
import com.unitel.laoads.domain.audience.Audience.DataType;
import com.unitel.laoads.domain.audience.Audience.Logical;
import com.unitel.laoads.domain.audience.Audience.Operator;
import com.unitel.laoads.domain.audience.Audience.Request;
import com.unitel.laoads.domain.audience.Audience.SelectedColumn;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kiểm tra + chuẩn hoá request Audience trước khi gửi service, theo quy tắc trong tài liệu tích hợp:
 * đúng 1 cột targetField "PHONE"; operator phải hợp kiểu dữ liệu; conditions luôn là mảng; logicalOperator AND|OR
 * nối với điều kiện kế tiếp (điều kiện giữa thiếu thì điền AND — OCS yêu cầu luôn có; điều kiện cuối bỏ đi).
 * Đầu vào dạng chuỗi để báo lỗi theo từng trường thay vì lỗi parse JSON chung.
 */
public final class AudienceRequests {

    public record RawColumn(String columnName, String dataType, String targetField) {
    }

    public record RawCondition(String columnName, String operator, String dataType, String logicalOperator, String value) {
    }

    public record RawRequest(String tableName, List<RawColumn> selectedColumns, List<RawCondition> conditions) {
    }

    static final int MAX_COLUMNS = 50;
    static final int MAX_CONDITIONS = 50;
    private static final Pattern TABLE = Pattern.compile("^[A-Za-z0-9_.$]{1,128}$");
    private static final Pattern COLUMN = Pattern.compile("^[A-Za-z0-9_$]{1,128}$");
    private static final Pattern TARGET = Pattern.compile("^[A-Z0-9_]{1,64}$");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d{1,19}$");

    private AudienceRequests() {
    }

    /** @throws LaoadsException VALIDATION_ERROR (field {@code request.…}) khi request sai */
    public static Request normalize(RawRequest raw) {
        List<FieldViolation> errors = new ArrayList<>();
        if (raw == null) {
            throw LaoadsException.validation(List.of(invalid("request")));
        }
        String table = raw.tableName() == null ? null : raw.tableName().trim();
        if (table == null || !TABLE.matcher(table).matches()) {
            errors.add(invalid("request.tableName"));
        }

        List<RawColumn> rawCols = raw.selectedColumns() == null ? List.of() : raw.selectedColumns();
        List<SelectedColumn> cols = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (rawCols.isEmpty() || rawCols.size() > MAX_COLUMNS) {
            errors.add(phoneRule());
        }
        int phones = 0;
        for (int i = 0; i < Math.min(rawCols.size(), MAX_COLUMNS); i++) {
            RawColumn c = rawCols.get(i);
            String path = "request.selectedColumns[" + i + "]";
            String name = c == null || c.columnName() == null ? null : c.columnName().trim();
            if (name == null || !COLUMN.matcher(name).matches() || !seen.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(invalid(path + ".columnName"));
                continue;
            }
            DataType type = dataType(c.dataType());
            if (type == null) {
                errors.add(invalid(path + ".dataType"));
                continue;
            }
            String target = c.targetField() == null || c.targetField().isBlank()
                    ? name.toUpperCase(Locale.ROOT) : c.targetField().trim().toUpperCase(Locale.ROOT);
            if (!TARGET.matcher(target).matches()) {
                errors.add(invalid(path + ".targetField"));
                continue;
            }
            if (Audience.PHONE.equals(target)) {
                phones++;
            }
            cols.add(new SelectedColumn(name, type, target));
        }
        if (!rawCols.isEmpty() && phones != 1) {
            errors.add(phoneRule());
        }

        List<RawCondition> rawConds = raw.conditions() == null ? List.of() : raw.conditions();
        if (rawConds.size() > MAX_CONDITIONS) {
            errors.add(new FieldViolation("request.conditions", "error.validation.range",
                    "Giá trị phải từ 0 đến " + MAX_CONDITIONS, 0, MAX_CONDITIONS));
        }
        List<Condition> conds = new ArrayList<>();
        for (int i = 0; i < Math.min(rawConds.size(), MAX_CONDITIONS); i++) {
            RawCondition c = rawConds.get(i);
            String path = "request.conditions[" + i + "]";
            String name = c == null || c.columnName() == null ? null : c.columnName().trim();
            if (name == null || !COLUMN.matcher(name).matches()) {
                errors.add(invalid(path + ".columnName"));
                continue;
            }
            DataType type = dataType(c.dataType());
            Operator op = operator(c.operator());
            if (type == null) {
                errors.add(invalid(path + ".dataType"));
                continue;
            }
            if (op == null) {
                errors.add(invalid(path + ".operator"));
                continue;
            }
            if (!op.accepts(type)) {
                errors.add(new FieldViolation(path + ".operator", "error.validation.audienceOperator",
                        "Toán tử " + op + " không áp dụng cho kiểu " + type, op.name(), type.name()));
                continue;
            }
            String value = c.value() == null ? null : c.value().trim();
            if (value == null || value.isEmpty() || value.length() > 500 || !valueMatches(type, value)) {
                errors.add(invalid(path + ".value"));
                continue;
            }
            Logical logical = null;
            boolean last = i == Math.min(rawConds.size(), MAX_CONDITIONS) - 1;
            if (!last) {
                logical = c.logicalOperator() == null || c.logicalOperator().isBlank() ? Logical.AND : logical(c.logicalOperator());
                if (logical == null) {
                    errors.add(invalid(path + ".logicalOperator"));
                    continue;
                }
            }
            conds.add(new Condition(name, op, type, logical, type == DataType.BOOLEAN ? value.toLowerCase(Locale.ROOT) : value));
        }
        if (!errors.isEmpty()) {
            throw LaoadsException.validation(errors);
        }
        return new Request(table, List.copyOf(cols), List.copyOf(conds));
    }

    static boolean valueMatches(DataType type, String v) {
        return switch (type) {
            case INT, LONG -> INTEGER.matcher(v).matches() && fitsRange(type, v);
            case DOUBLE -> {
                try {
                    new BigDecimal(v);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(v);
                    yield true;
                } catch (DateTimeParseException e) {
                    yield false;
                }
            }
            case BOOLEAN -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
            case STRING -> true;
        };
    }

    private static boolean fitsRange(DataType type, String v) {
        try {
            long n = Long.parseLong(v);
            return type == DataType.LONG || (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static DataType dataType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DataType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Operator operator(String raw) {
        try {
            return raw == null ? null : Operator.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Logical logical(String raw) {
        try {
            return Logical.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static FieldViolation phoneRule() {
        return new FieldViolation("request.selectedColumns", "error.validation.audiencePhoneColumn",
                "Cần chọn đúng một cột số thuê bao (PHONE)");
    }

    private static FieldViolation invalid(String field) {
        return new FieldViolation(field, "error.validation.invalidValue", "Giá trị không hợp lệ");
    }
}
