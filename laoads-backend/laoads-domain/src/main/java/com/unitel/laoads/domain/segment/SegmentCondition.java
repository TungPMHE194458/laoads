package com.unitel.laoads.domain.segment;

/**
 * Một điều kiện lọc thuê bao. {@code join} nối với điều kiện TRƯỚC (bỏ qua ở điều kiện đầu tiên).
 * Toán tử: EQ NEQ GT GTE LT LTE BETWEEN (dùng valueTo) IN (giá trị cách nhau dấu phẩy) IS_NULL.
 */
public record SegmentCondition(String field, String operator, String value, String valueTo, String join) {
}
