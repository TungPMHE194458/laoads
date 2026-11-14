package com.unitel.laoads.domain.pricing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PriceQuoteRepository extends JpaRepository<PriceQuote, Long> {

    Optional<PriceQuote> findFirstByStatusOrderByLockedAtDesc(PriceQuoteStatus status);

    boolean existsByName(String name);

    @Query(value = "SELECT q FROM PriceQuote q WHERE (:status IS NULL OR q.status = :status) "
            + "ORDER BY q.createdAt DESC, q.id DESC",
            countQuery = "SELECT COUNT(q) FROM PriceQuote q WHERE (:status IS NULL OR q.status = :status)")
    Page<PriceQuote> search(@Param("status") PriceQuoteStatus status, Pageable pageable);

    /**
     * Chuyển bảng LOCKED hiện hành → ENDED. Bulk update chạy NGAY (trước khi khoá bảng mới) để không vi phạm
     * unique index "tối đa 1 LOCKED" — nếu để Hibernate flush (order_updates sort theo id) có thể sai thứ tự.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PriceQuote q SET q.status = :ended, q.endedAt = :now, q.endedBy = :actor, "
            + "q.updatedAt = :now, q.updatedBy = :actor WHERE q.status = :locked AND q.id <> :exceptId")
    int endLocked(@Param("locked") PriceQuoteStatus locked,
                  @Param("ended") PriceQuoteStatus ended,
                  @Param("exceptId") Long exceptId,
                  @Param("now") Instant now,
                  @Param("actor") String actor);
}
