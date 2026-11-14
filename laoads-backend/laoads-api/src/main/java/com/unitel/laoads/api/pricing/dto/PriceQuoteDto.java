package com.unitel.laoads.api.pricing.dto;

import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.pricing.PriceQuote;
import com.unitel.laoads.domain.pricing.PriceQuoteStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** {@code currency} luôn 'LAK'; {@code lockedBy}/{@code createdBy} = username. */
public record PriceQuoteDto(
        Long id,
        String name,
        String note,
        PriceQuoteStatus status,
        String currency,
        List<PriceItemInput> items,
        OffsetDateTime lockedAt,
        String lockedBy,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt
) {
    public static PriceQuoteDto from(PriceQuote q) {
        return new PriceQuoteDto(q.getId(), q.getName(), q.getNote(), q.getStatus(), q.getCurrency(),
                q.getItems().stream().map(PriceItemInput::from).toList(),
                Times.toOffset(q.getLockedAt()), q.getLockedBy(),
                Times.toOffset(q.getCreatedAt()), q.getCreatedBy(), Times.toOffset(q.getUpdatedAt()));
    }
}
