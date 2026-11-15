package com.unitel.laoads.domain.segment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Danh mục thuộc tính thuê bao dùng để lọc tệp khách hàng. Đây cũng là DANH SÁCH TRẮNG cột của bảng
 * LADS_SUBSCRIBER (staging dữ liệu trích xuất từ BI all_report_stl_new) — SegmentQueryBuilder chỉ sinh SQL
 * với các cột này, giá trị luôn đi qua bind parameter.
 */
public final class SegmentAttributeCatalog {

    public enum DataType { STRING, NUMBER, BOOLEAN }

    public record Attribute(String field, String group, DataType dataType, List<String> options) {

        /** Tên cột trong LADS_SUBSCRIBER (= field viết hoa; field chỉ gồm a-z0-9_). */
        public String column() {
            return field.toUpperCase(java.util.Locale.ROOT);
        }
    }

    public static final List<Attribute> ATTRIBUTES = List.of(
            new Attribute("province_code_active", "PROFILE", DataType.STRING,
                    List.of("VTE", "LPB", "SVK", "CPS", "XKH", "OUD", "BOK")),
            new Attribute("customer_age", "PROFILE", DataType.NUMBER, null),
            new Attribute("gender", "PROFILE", DataType.STRING, List.of("M", "F")),
            new Attribute("tenure", "PROFILE", DataType.NUMBER, null),
            new Attribute("act_status", "PROFILE", DataType.STRING, List.of("00", "01", "02", "03")),
            new Attribute("device_type", "DEVICE", DataType.STRING, List.of("SMARTPHONE", "FEATURE_PHONE", "MODEM")),
            new Attribute("infras_data_4g", "DEVICE", DataType.BOOLEAN, null),
            new Attribute("is_smartphone", "DEVICE", DataType.BOOLEAN, null),
            new Attribute("real_package_data", "PACKAGE", DataType.STRING,
                    List.of("D5K", "D10K", "M50", "M99", "M150", "NONE")),
            new Attribute("data_package", "PACKAGE", DataType.STRING, List.of("DAY", "WEEK", "MONTH", "NONE")),
            new Attribute("g_volume", "USAGE", DataType.NUMBER, null),
            new Attribute("t_org_charge", "USAGE", DataType.NUMBER, null),
            new Attribute("laostv_total_volume", "USAGE", DataType.NUMBER, null),
            new Attribute("login_superapp", "USAGE", DataType.NUMBER, null),
            new Attribute("active_umoney", "BALANCE", DataType.BOOLEAN, null),
            new Attribute("balance_basic", "BALANCE", DataType.NUMBER, null));

    private static final Map<String, Attribute> BY_FIELD =
            ATTRIBUTES.stream().collect(Collectors.toUnmodifiableMap(Attribute::field, Function.identity()));

    private SegmentAttributeCatalog() {
    }

    public static Optional<Attribute> find(String field) {
        return Optional.ofNullable(field == null ? null : BY_FIELD.get(field));
    }
}
