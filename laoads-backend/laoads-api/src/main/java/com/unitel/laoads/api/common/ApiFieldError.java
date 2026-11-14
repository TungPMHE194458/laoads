package com.unitel.laoads.api.common;

/** Phần tử {@code errors[]} trong envelope lỗi VALIDATION_ERROR. */
public record ApiFieldError(String field, String message) {
}
