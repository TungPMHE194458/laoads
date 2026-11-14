package com.unitel.laoads.domain.pricing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * LADS_PRICE_QUOTE — bảng giá. Unique function-based index đảm bảo tối đa 1 bảng LOCKED (xem V1).
 * Items EAGER + BatchSize: DTO luôn cần items, open-in-view=false nên load sẵn.
 */
@Entity
@Table(name = "LADS_PRICE_QUOTE")
@Getter
@Setter
@NoArgsConstructor
public class PriceQuote {

    public static final String CURRENCY_LAK = "LAK";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_price_quote_seq")
    @SequenceGenerator(name = "lads_price_quote_seq", sequenceName = "LADS_PRICE_QUOTE_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "NOTE", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    private PriceQuoteStatus status;

    @Column(name = "CURRENCY", nullable = false, length = 8)
    private String currency = CURRENCY_LAK;

    @Column(name = "LOCKED_AT")
    private Instant lockedAt;

    @Column(name = "LOCKED_BY", length = 64)
    private String lockedBy;

    @Column(name = "ENDED_AT")
    private Instant endedAt;

    @Column(name = "ENDED_BY", length = 64)
    private String endedBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<PriceQuoteItem> items = new ArrayList<>();

    public boolean isDraft() {
        return status == PriceQuoteStatus.DRAFT;
    }
}
