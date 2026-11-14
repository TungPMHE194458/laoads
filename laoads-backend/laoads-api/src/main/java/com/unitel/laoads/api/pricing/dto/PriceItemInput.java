package com.unitel.laoads.api.pricing.dto;

import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.pricing.PriceItemCommand;
import com.unitel.laoads.domain.pricing.PriceQuoteItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Dòng giá — dùng cho cả request và response (contract §7). {@code timePricePercentage=true} →
 * {@code timePrice} là % (0–100).
 */
public record PriceItemInput(
        @NotNull ActionType actionType,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal timePrice,
        @NotNull Boolean timePricePercentage
) {
    public PriceItemCommand toCommand() {
        return new PriceItemCommand(actionType, unitPrice, timePrice, Boolean.TRUE.equals(timePricePercentage));
    }

    public static PriceItemInput from(PriceQuoteItem it) {
        return new PriceItemInput(it.getActionType(), it.getUnitPrice(), it.getTimePrice(),
                Boolean.TRUE.equals(it.getTimePricePercentage()));
    }
}
