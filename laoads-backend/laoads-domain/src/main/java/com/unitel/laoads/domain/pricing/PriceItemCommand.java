package com.unitel.laoads.domain.pricing;

import java.math.BigDecimal;

/** Dòng giá đầu vào (tạo/sửa bảng giá). */
public record PriceItemCommand(ActionType actionType, BigDecimal unitPrice, BigDecimal timePrice,
                               boolean timePricePercentage) {
}
