package com.unitel.laoads.domain.common;

/**
 * Lỗi validate gắn với 1 field, phát sinh từ tầng service (vd trùng actionType trong bảng giá).
 * Message được dịch ở GlobalExceptionHandler theo {@code messageKey} + {@code args};
 * {@code defaultMessage} (tiếng Việt) dùng khi bundle không có key.
 */
public record FieldViolation(String field, String messageKey, String defaultMessage, Object... args) {
}
