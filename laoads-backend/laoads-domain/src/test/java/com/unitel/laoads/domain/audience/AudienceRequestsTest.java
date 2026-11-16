package com.unitel.laoads.domain.audience;

import com.unitel.laoads.domain.audience.Audience.DataType;
import com.unitel.laoads.domain.audience.Audience.Logical;
import com.unitel.laoads.domain.audience.AudienceRequests.RawColumn;
import com.unitel.laoads.domain.audience.AudienceRequests.RawCondition;
import com.unitel.laoads.domain.audience.AudienceRequests.RawRequest;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienceRequestsTest {

    private static final RawColumn PHONE = new RawColumn("phone_number", "string", "phone");
    private static final RawColumn AGE = new RawColumn("age", "INT", null);

    private static List<String> fields(Throwable e) {
        return ((LaoadsException) e).getErrors().stream().map(FieldViolation::field).toList();
    }

    @Test
    @DisplayName("chuẩn hoá: kiểu / targetField viết hoa, điều kiện giữa thiếu logicalOperator → AND, điều kiện cuối bỏ")
    void normalizes() {
        Audience.Request r = AudienceRequests.normalize(new RawRequest(" subs ", List.of(PHONE, AGE), List.of(
                new RawCondition("age", "greater_equal_than", "int", null, "18"),
                new RawCondition("active", "IS", "BOOLEAN", "OR", "TRUE"))));

        assertThat(r.tableName()).isEqualTo("subs");
        assertThat(r.columns()).extracting(Audience.SelectedColumn::targetField).containsExactly("PHONE", "AGE");
        assertThat(r.columns().get(0).dataType()).isEqualTo(DataType.STRING);
        assertThat(r.filters().get(0).logicalOperator()).isEqualTo(Logical.AND);
        assertThat(r.filters().get(1).logicalOperator()).as("điều kiện cuối không nối gì").isNull();
        assertThat(r.filters().get(1).value()).isEqualTo("true");
        assertThat(r.phoneColumn().columnName()).isEqualTo("phone_number");
        assertThat(AudienceRequests.normalize(new RawRequest("t", List.of(PHONE), null)).filters()).isEmpty();
    }

    @Test
    @DisplayName("phải có đúng 1 cột PHONE")
    void exactlyOnePhone() {
        assertThatThrownBy(() -> AudienceRequests.normalize(new RawRequest("t", List.of(AGE), List.of())))
                .satisfies(e -> assertThat(fields(e)).contains("request.selectedColumns"));
        assertThatThrownBy(() -> AudienceRequests.normalize(new RawRequest("t",
                List.of(PHONE, new RawColumn("msisdn", "STRING", "PHONE")), List.of())))
                .satisfies(e -> assertThat(fields(e)).contains("request.selectedColumns"));
        assertThatThrownBy(() -> AudienceRequests.normalize(new RawRequest("t", List.of(), List.of())))
                .satisfies(e -> assertThat(fields(e)).contains("request.selectedColumns"));
    }

    @Test
    @DisplayName("operator phải hợp kiểu, giá trị đúng kiểu, tên bảng / cột an toàn")
    void rejectsBadConditions() {
        assertThatThrownBy(() -> AudienceRequests.normalize(new RawRequest("bad table;", List.of(PHONE), List.of(
                new RawCondition("age", "LIKE", "INT", null, "1"),
                new RawCondition("age", "GREATER_THAN", "INT", null, "abc"),
                new RawCondition("d", "IS", "DATE", null, "16/11/2026"),
                new RawCondition("b", "IS", "BOOLEAN", null, "yes"),
                new RawCondition("x", "BETWEEN", "INT", null, "1"),
                new RawCondition("x; drop", "IS", "STRING", null, "1"),
                new RawCondition("n", "IS", "INT", null, "99999999999"),
                new RawCondition("s", "IS", "STRING", "XOR", "a"),
                new RawCondition("s", "IS", "STRING", null, "a")))))
                .satisfies(e -> assertThat(fields(e)).containsExactly(
                        "request.tableName",
                        "request.conditions[0].operator",
                        "request.conditions[1].value",
                        "request.conditions[2].value",
                        "request.conditions[3].value",
                        "request.conditions[4].operator",
                        "request.conditions[5].columnName",
                        "request.conditions[6].value",
                        "request.conditions[7].logicalOperator"));
    }

    @Test
    @DisplayName("kiểu cột từ catalog (Hive / MySQL) chuẩn hoá về 6 kiểu của tài liệu")
    void normalizesCatalogTypes() {
        assertThat(DataType.normalize("string")).isEqualTo(DataType.STRING);
        assertThat(DataType.normalize("varchar(20)")).isEqualTo(DataType.STRING);
        assertThat(DataType.normalize("bigint")).isEqualTo(DataType.LONG);
        assertThat(DataType.normalize("INT")).isEqualTo(DataType.INT);
        assertThat(DataType.normalize("decimal(10,2)")).isEqualTo(DataType.DOUBLE);
        assertThat(DataType.normalize("timestamp")).isEqualTo(DataType.DATE);
        assertThat(DataType.normalize("tinyint")).isEqualTo(DataType.INT);
        assertThat(DataType.normalize("boolean")).isEqualTo(DataType.BOOLEAN);
        assertThat(DataType.normalize(null)).isEqualTo(DataType.STRING);
    }
}
