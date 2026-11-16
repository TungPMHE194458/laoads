package com.unitel.laoads.domain.audience;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kiểu dữ liệu dùng chung với Audience API (OCS Audience / BCCS3 Audience) — xem docs/requirements
 * "Tài liệu API tích hợp" và "Import vào Postman cho OCS Audience và BBCS3 Audience". Tên trường giữ đúng JSON của API.
 */
public final class Audience {

    private Audience() {
    }

    /** Nguồn: OCS (Hive, có offset để nạp tiếp) và BCCS3 (MySQL). */
    public enum Source {
        OCS, BCCS3;

        public boolean supportsOffset() {
            return this == OCS;
        }
    }

    /** Kiểu dữ liệu đã chuẩn hoá theo tài liệu. */
    public enum DataType {
        BOOLEAN, INT, LONG, DOUBLE, DATE, STRING;

        /** Chuẩn hoá kiểu cột trả về từ catalog (Hive viết thường "string", "bigint", "decimal(10,2)"…). */
        public static DataType normalize(String raw) {
            String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            int paren = t.indexOf('(');
            if (paren >= 0) {
                t = t.substring(0, paren);
            }
            return switch (t) {
                case "boolean", "bool", "bit" -> BOOLEAN;
                case "int", "integer", "smallint", "tinyint", "mediumint" -> INT;
                case "long", "bigint" -> LONG;
                case "double", "float", "real", "decimal", "numeric", "number" -> DOUBLE;
                case "date", "datetime", "timestamp" -> DATE;
                default -> STRING;
            };
        }
    }

    /** Operator theo bảng trong tài liệu, kèm các kiểu dữ liệu hợp lệ. */
    public enum Operator {
        IS(Set.of(DataType.values())),
        NOT(Set.of(DataType.values())),
        LIKE(Set.of(DataType.STRING)),
        NOT_LIKE(Set.of(DataType.STRING)),
        GREATER_THAN(Set.of(DataType.INT, DataType.LONG, DataType.DOUBLE, DataType.DATE, DataType.STRING)),
        GREATER_EQUAL_THAN(Set.of(DataType.INT, DataType.LONG, DataType.DOUBLE, DataType.DATE, DataType.STRING)),
        LESS_THAN(Set.of(DataType.INT, DataType.LONG, DataType.DOUBLE, DataType.DATE, DataType.STRING)),
        LESS_EQUAL_THAN(Set.of(DataType.INT, DataType.LONG, DataType.DOUBLE, DataType.DATE, DataType.STRING));

        private final Set<DataType> types;

        Operator(Set<DataType> types) {
            this.types = types;
        }

        public boolean accepts(DataType t) {
            return types.contains(t);
        }
    }

    public enum Logical { AND, OR }

    /** targetField của cột số thuê bao — bắt buộc đúng 1 cột. */
    public static final String PHONE = "PHONE";

    public record Column(String name, DataType dataType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedColumn(String columnName, DataType dataType, String targetField) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Condition(String columnName, Operator operator, DataType dataType, Logical logicalOperator,
                            String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String tableName, List<SelectedColumn> selectedColumns, List<Condition> conditions) {

        public List<SelectedColumn> columns() {
            return selectedColumns == null ? List.of() : selectedColumns;
        }

        public List<Condition> filters() {
            return conditions == null ? List.of() : conditions;
        }

        /** Cột mang targetField PHONE (đã kiểm tra hợp lệ thì luôn có đúng 1). */
        public SelectedColumn phoneColumn() {
            return columns().stream().filter(c -> PHONE.equalsIgnoreCase(c.targetField())).findFirst().orElse(null);
        }
    }

    public record VerifyResult(boolean valid, String message) {
    }

    public record SourceInfo(Source source, boolean configured, String mode) {
    }
}
