package com.unitel.laoads.domain.pricing;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceQuoteServiceTest {

    @Mock
    PriceQuoteRepository repo;

    PriceQuoteService service;

    @BeforeEach
    void setUp() {
        service = new PriceQuoteService(repo);
    }

    @Test
    @DisplayName("lock: DRAFT → LOCKED, bảng LOCKED cũ chuyển ENDED TRƯỚC khi khoá bảng mới")
    void lockMovesPreviousLockedToEndedFirst() {
        PriceQuote draft = quote(5L, PriceQuoteStatus.DRAFT);
        when(repo.findById(5L)).thenReturn(Optional.of(draft));
        AtomicReference<PriceQuoteStatus> statusWhenEnding = new AtomicReference<>();
        doAnswer(inv -> {
            statusWhenEnding.set(draft.getStatus());
            return 1;
        }).when(repo).endLocked(any(), any(), anyLong(), any(), anyString());

        PriceQuote locked = service.lock(5L, "superadmin");

        verify(repo).endLocked(eq(PriceQuoteStatus.LOCKED), eq(PriceQuoteStatus.ENDED), eq(5L),
                any(Instant.class), eq("superadmin"));
        // Unique index "tối đa 1 LOCKED": bảng mới còn DRAFT lúc kết thúc bảng cũ
        assertThat(statusWhenEnding.get()).isEqualTo(PriceQuoteStatus.DRAFT);
        assertThat(locked.getStatus()).isEqualTo(PriceQuoteStatus.LOCKED);
        assertThat(locked.getLockedBy()).isEqualTo("superadmin");
        assertThat(locked.getLockedAt()).isNotNull();
    }

    @Test
    void lockNonDraftRejectedWithoutTouchingOthers() {
        when(repo.findById(6L)).thenReturn(Optional.of(quote(6L, PriceQuoteStatus.LOCKED)));
        assertCode(() -> service.lock(6L, "superadmin"), ErrorCode.PRICE_QUOTE_NOT_DRAFT);
        verify(repo, never()).endLocked(any(), any(), anyLong(), any(), anyString());
    }

    @Test
    void updateEndedRejected() {
        when(repo.findById(7L)).thenReturn(Optional.of(quote(7L, PriceQuoteStatus.ENDED)));
        assertCode(() -> service.update(7L, "x", null, List.of(), "superadmin"), ErrorCode.PRICE_QUOTE_NOT_DRAFT);
    }

    @Test
    void deleteOnlyDraft() {
        PriceQuote locked = quote(8L, PriceQuoteStatus.LOCKED);
        when(repo.findById(8L)).thenReturn(Optional.of(locked));
        assertCode(() -> service.delete(8L, "superadmin"), ErrorCode.PRICE_QUOTE_NOT_DRAFT);
        verify(repo, never()).delete(any());

        PriceQuote draft = quote(9L, PriceQuoteStatus.DRAFT);
        when(repo.findById(9L)).thenReturn(Optional.of(draft));
        service.delete(9L, "superadmin");
        InOrder order = inOrder(repo);
        order.verify(repo).delete(draft);
    }

    @Test
    void missingQuoteIsNotFound() {
        when(repo.findById(1L)).thenReturn(Optional.empty());
        assertCode(() -> service.get(1L), ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("validate: trùng actionType → VALIDATION_ERROR với field items[i].actionType")
    void duplicateActionTypeRejected() {
        List<PriceItemCommand> items = List.of(item(ActionType.ADD_SMS, false, 0), item(ActionType.ADD_SMS, false, 0));
        assertThatThrownBy(() -> PriceQuoteService.validateItems(items))
                .isInstanceOfSatisfying(LaoadsException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.getErrors()).extracting(v -> v.field()).containsExactly("items[1].actionType");
                });
    }

    @Test
    void percentageAbove100Rejected() {
        List<PriceItemCommand> items = List.of(item(ActionType.TOP_UP, true, 101));
        assertThatThrownBy(() -> PriceQuoteService.validateItems(items))
                .isInstanceOfSatisfying(LaoadsException.class,
                        e -> assertThat(e.getErrors()).extracting(v -> v.field()).containsExactly("items[0].timePrice"));
        PriceQuoteService.validateItems(List.of(item(ActionType.TOP_UP, false, 101))); // không phải % → hợp lệ
    }

    @Test
    @DisplayName("applyItems: cập nhật tại chỗ theo actionType, bỏ dòng không còn, thêm dòng mới")
    void applyItemsMergesByActionType() {
        PriceQuote q = quote(1L, PriceQuoteStatus.DRAFT);
        PriceQuoteService.applyItems(q, List.of(item(ActionType.ADD_SMS, false, 1), item(ActionType.TOP_UP, false, 2)));
        PriceQuoteItem smsBefore = q.getItems().get(0);

        PriceQuoteService.applyItems(q, List.of(item(ActionType.ADD_DATA, true, 5), item(ActionType.ADD_SMS, false, 9)));

        assertThat(q.getItems()).extracting(PriceQuoteItem::getActionType)
                .containsExactly(ActionType.ADD_DATA, ActionType.ADD_SMS);
        assertThat(q.getItems().get(1)).isSameAs(smsBefore);
        assertThat(smsBefore.getTimePrice()).isEqualByComparingTo("9");
        assertThat(q.getItems()).extracting(PriceQuoteItem::getSortOrder).containsExactly(0, 1);
    }

    private static PriceQuote quote(long id, PriceQuoteStatus status) {
        PriceQuote q = new PriceQuote();
        q.setId(id);
        q.setName("Q" + id);
        q.setStatus(status);
        q.setCreatedAt(Instant.now());
        return q;
    }

    private static PriceItemCommand item(ActionType t, boolean pct, int timePrice) {
        return new PriceItemCommand(t, BigDecimal.TEN, BigDecimal.valueOf(timePrice), pct);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(LaoadsException.class)
                .extracting(e -> ((LaoadsException) e).getCode())
                .isEqualTo(code);
    }
}
