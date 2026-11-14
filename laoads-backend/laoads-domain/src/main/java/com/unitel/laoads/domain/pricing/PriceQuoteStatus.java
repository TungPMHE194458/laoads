package com.unitel.laoads.domain.pricing;

/** DRAFT (sửa/xoá được) → LOCKED (đang áp dụng, tối đa 1) → ENDED (bị thay bởi bảng LOCKED mới). */
public enum PriceQuoteStatus {
    DRAFT,
    LOCKED,
    ENDED
}
