package com.unitel.laoads.domain.segment;

import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.segment.SegmentAttributeCatalog.Attribute;
import com.unitel.laoads.domain.segment.SegmentAttributeCatalog.DataType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sinh mệnh đề WHERE an toàn trên LADS_SUBSCRIBER từ điều kiện lọc (vai trò "SqlGuard"): tên cột chỉ lấy từ
 * {@link SegmentAttributeCatalog}, giá trị luôn là bind parameter, toán tử theo kiểu dữ liệu.
 * Điều kiện nối theo thứ tự với AND/OR của từng điều kiện (độ ưu tiên SQL chuẩn: AND trước OR).
 */
public final class SegmentQueryBuilder {

    public record Where(String sql, List<Object> params) {
    }

    private static final Set<String> NUMBER_OPS = Set.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "BETWEEN", "IN", "IS_NULL");
    private static final Set<String> STRING_OPS = Set.of("EQ", "NEQ", "IN", "IS_NULL");
    private static final Set<String> BOOLEAN_OPS = Set.of("EQ", "NEQ", "IS_NULL");
    private static final int MAX_CONDITIONS = 20;
    private static final int MAX_IN_VALUES = 200;

    private SegmentQueryBuilder() {
    }

    public static Where build(List<SegmentCondition> conditions) {
        List<SegmentCondition> list = conditions == null ? List.of() : conditions;
        if (list.size() > MAX_CONDITIONS) {
            throw invalid("conditions", "error.validation.invalidValue", "Quá nhiều điều kiện");
        }
        if (list.isEmpty()) {
            return new Where("1 = 1", List.of());
        }
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            SegmentCondition c = list.get(i);
            String path = "conditions[" + i + "]";
            if (i > 0) {
                sql.append("OR".equalsIgnoreCase(c.join()) ? " OR " : " AND ");
            }
            sql.append(predicate(c, path, params));
        }
        return new Where(sql.toString(), params);
    }

    private static String predicate(SegmentCondition c, String path, List<Object> params) {
        Attribute a = SegmentAttributeCatalog.find(c.field()).orElseThrow(() ->
                invalid(path + ".field", "error.validation.unknownField", "Thuộc tính không hợp lệ", c.field()));
        String op = c.operator() == null ? "" : c.operator().toUpperCase(Locale.ROOT);
        Set<String> allowed = switch (a.dataType()) {
            case NUMBER -> NUMBER_OPS;
            case STRING -> STRING_OPS;
            case BOOLEAN -> BOOLEAN_OPS;
        };
        if (!allowed.contains(op)) {
            throw invalid(path + ".operator", "error.validation.badOperator", "Toán tử không hợp lệ", op);
        }
        String col = a.column();
        switch (op) {
            case "IS_NULL" -> {
                return col + " IS NULL";
            }
            case "BETWEEN" -> {
                params.add(value(a, c.value(), path + ".value"));
                params.add(value(a, c.valueTo(), path + ".valueTo"));
                return col + " BETWEEN ? AND ?";
            }
            case "IN" -> {
                List<String> parts = Arrays.stream((c.value() == null ? "" : c.value()).split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (parts.isEmpty() || parts.size() > MAX_IN_VALUES) {
                    throw invalid(path + ".value", "error.validation.invalidValue", "Giá trị không hợp lệ");
                }
                for (String s : parts) {
                    params.add(value(a, s, path + ".value"));
                }
                return col + " IN (" + String.join(", ", java.util.Collections.nCopies(parts.size(), "?")) + ")";
            }
            default -> {
                params.add(value(a, c.value(), path + ".value"));
                String sqlOp = switch (op) {
                    case "EQ" -> "=";
                    case "NEQ" -> "<>";
                    case "GT" -> ">";
                    case "GTE" -> ">=";
                    case "LT" -> "<";
                    default -> "<=";
                };
                // NEQ giữ cả bản ghi NULL như người dùng mong đợi ("khác X")
                return "NEQ".equals(op) ? "(" + col + " <> ? OR " + col + " IS NULL)" : col + " " + sqlOp + " ?";
            }
        }
    }

    private static Object value(Attribute a, String raw, String path) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            throw invalid(path, "error.validation.invalidValue", "Giá trị không hợp lệ");
        }
        if (a.dataType() == DataType.NUMBER) {
            try {
                return new BigDecimal(v);
            } catch (NumberFormatException e) {
                throw invalid(path, "error.validation.badNumber", "Giá trị số không hợp lệ", v);
            }
        }
        if (a.dataType() == DataType.BOOLEAN) {
            return "true".equalsIgnoreCase(v) || "1".equals(v) ? 1 : 0;
        }
        if (v.length() > 64) {
            throw invalid(path, "error.validation.invalidValue", "Giá trị không hợp lệ");
        }
        return v;
    }

    private static LaoadsException invalid(String field, String key, String def, Object... args) {
        return LaoadsException.validation(List.of(new FieldViolation(field, key, def, args)));
    }
}
