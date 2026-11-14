package com.unitel.laoads.domain.pricing;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bảng giá (Quản lý chi phí) — contract §7.
 * Vòng đời: DRAFT → (lock) → LOCKED → (bảng khác được lock) → ENDED. Chỉ DRAFT được sửa/xoá.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceQuoteService {

    static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private final PriceQuoteRepository repo;

    @Transactional(readOnly = true)
    public Page<PriceQuote> list(PriceQuoteStatus status, Pageable pageable) {
        return repo.search(status, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<PriceQuote> active() {
        return repo.findFirstByStatusOrderByLockedAtDesc(PriceQuoteStatus.LOCKED);
    }

    @Transactional(readOnly = true)
    public PriceQuote get(long id) {
        return repo.findById(id).orElseThrow(() -> LaoadsException.notFound("error.notFound.priceQuote",
                "Không tìm thấy bảng giá id=" + id, id));
    }

    @Transactional
    public PriceQuote create(String name, String note, List<PriceItemCommand> items, String actor) {
        validateItems(items);
        PriceQuote q = new PriceQuote();
        q.setName(name.trim());
        q.setNote(Texts.trimToNull(note));
        q.setStatus(PriceQuoteStatus.DRAFT);
        q.setCurrency(PriceQuote.CURRENCY_LAK);
        q.setCreatedAt(Instant.now());
        q.setCreatedBy(actor);
        applyItems(q, items);
        PriceQuote saved = repo.save(q);
        log.info("[PRICE] {} tạo bảng giá #{} '{}' ({} dòng)", actor, saved.getId(), saved.getName(), items.size());
        return saved;
    }

    /** Chỉ DRAFT; ngược lại 409 PRICE_QUOTE_NOT_DRAFT. */
    @Transactional
    public PriceQuote update(long id, String name, String note, List<PriceItemCommand> items, String actor) {
        PriceQuote q = get(id);
        requireDraft(q);
        validateItems(items);
        q.setName(name.trim());
        q.setNote(Texts.trimToNull(note));
        q.setUpdatedAt(Instant.now());
        q.setUpdatedBy(actor);
        applyItems(q, items);
        return q;
    }

    /** DRAFT → LOCKED; bảng LOCKED cũ (nếu có) → ENDED trong cùng transaction. */
    @Transactional
    public PriceQuote lock(long id, String actor) {
        PriceQuote q = get(id);
        requireDraft(q);
        Instant now = Instant.now();
        int ended = repo.endLocked(PriceQuoteStatus.LOCKED, PriceQuoteStatus.ENDED, q.getId(), now, actor);
        q.setStatus(PriceQuoteStatus.LOCKED);
        q.setLockedAt(now);
        q.setLockedBy(actor);
        q.setUpdatedAt(now);
        q.setUpdatedBy(actor);
        log.info("[PRICE] {} khoá bảng giá #{} (kết thúc {} bảng LOCKED cũ)", actor, q.getId(), ended);
        return q;
    }

    /** Chỉ DRAFT; ngược lại 409 PRICE_QUOTE_NOT_DRAFT. */
    @Transactional
    public PriceQuote delete(long id, String actor) {
        PriceQuote q = get(id);
        requireDraft(q);
        repo.delete(q);
        log.info("[PRICE] {} xoá bảng giá #{}", actor, id);
        return q;
    }

    static void requireDraft(PriceQuote q) {
        if (!q.isDraft()) {
            throw new LaoadsException(ErrorCode.PRICE_QUOTE_NOT_DRAFT,
                    "Chỉ thao tác được với bảng giá ở trạng thái DRAFT");
        }
    }

    /** Không trùng actionType; nếu timePrice là phần trăm thì ≤ 100. */
    static void validateItems(List<PriceItemCommand> items) {
        List<FieldViolation> errors = new ArrayList<>();
        Set<ActionType> seen = EnumSet.noneOf(ActionType.class);
        for (int i = 0; i < items.size(); i++) {
            PriceItemCommand it = items.get(i);
            if (!seen.add(it.actionType())) {
                errors.add(new FieldViolation("items[" + i + "].actionType", "error.validation.duplicateActionType",
                        "Loại hành động bị trùng: " + it.actionType(), it.actionType().name()));
            }
            if (it.timePricePercentage() && it.timePrice().compareTo(MAX_PERCENT) > 0) {
                errors.add(new FieldViolation("items[" + i + "].timePrice", "error.validation.percentMax",
                        "Phần trăm không được vượt quá 100"));
            }
        }
        if (!errors.isEmpty()) {
            throw LaoadsException.validation(errors);
        }
    }

    /**
     * Gộp items theo actionType: dòng đã có → cập nhật tại chỗ, dòng mới → thêm, dòng không còn → orphan delete.
     * Không xoá-rồi-chèn lại cùng actionType vì Hibernate flush INSERT trước DELETE → vi phạm unique.
     */
    static void applyItems(PriceQuote q, List<PriceItemCommand> items) {
        Map<ActionType, PriceQuoteItem> existing = new EnumMap<>(ActionType.class);
        for (PriceQuoteItem it : q.getItems()) {
            existing.put(it.getActionType(), it);
        }
        List<PriceQuoteItem> result = new ArrayList<>(items.size());
        int order = 0;
        for (PriceItemCommand cmd : items) {
            PriceQuoteItem it = existing.remove(cmd.actionType());
            if (it == null) {
                it = new PriceQuoteItem();
                it.setQuote(q);
                it.setActionType(cmd.actionType());
            }
            it.setUnitPrice(cmd.unitPrice());
            it.setTimePrice(cmd.timePrice());
            it.setTimePricePercentage(cmd.timePricePercentage());
            it.setSortOrder(order++);
            result.add(it);
        }
        q.getItems().clear();
        q.getItems().addAll(result);
    }
}
