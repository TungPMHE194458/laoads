package com.unitel.laoads.domain.segment;

import com.unitel.laoads.domain.common.LaoadsException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Toán tử "chứa" (SRS 3.5.1.2) — chỉ cho thuộc tính chuỗi, bind tham số, escape ký tự đại diện của LIKE. */
class SegmentQueryBuilderTest {

    @Test
    void containsBuildsCaseInsensitiveLikeWithEscapedValue() {
        SegmentQueryBuilder.Where w = SegmentQueryBuilder.build(List.of(
                new SegmentCondition("device_type", "CONTAINS", "Smart_%", null, null)));
        assertThat(w.sql()).contains("LOWER(").contains("LIKE ? ESCAPE '!'");
        assertThat(w.params()).containsExactly("%smart!_!%%");
    }

    @Test
    void containsIsRejectedForNumbers() {
        assertThatThrownBy(() -> SegmentQueryBuilder.build(List.of(
                new SegmentCondition("g_volume", "CONTAINS", "10", null, null))))
                .isInstanceOf(LaoadsException.class);
    }
}
