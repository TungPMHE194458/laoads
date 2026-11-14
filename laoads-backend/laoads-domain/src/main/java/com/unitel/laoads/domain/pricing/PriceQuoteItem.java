package com.unitel.laoads.domain.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** LADS_PRICE_QUOTE_ITEM — 1 dòng giá cho 1 action type (unique theo bảng giá). */
@Entity
@Table(name = "LADS_PRICE_QUOTE_ITEM")
@Getter
@Setter
@NoArgsConstructor
public class PriceQuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_price_quote_item_seq")
    @SequenceGenerator(name = "lads_price_quote_item_seq", sequenceName = "LADS_PRICE_QUOTE_ITEM_SEQ",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "QUOTE_ID", nullable = false)
    private PriceQuote quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTION_TYPE", nullable = false, length = 32)
    private ActionType actionType;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "TIME_PRICE", nullable = false, precision = 19, scale = 4)
    private BigDecimal timePrice;

    /** NUMBER(1): true = timePrice là phần trăm. */
    @Column(name = "TIME_PRICE_PERCENTAGE", nullable = false)
    private Boolean timePricePercentage;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;
}
