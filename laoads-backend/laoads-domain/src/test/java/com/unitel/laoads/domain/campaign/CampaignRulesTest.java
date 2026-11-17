package com.unitel.laoads.domain.campaign;

import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.common.FieldViolation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Quy tắc SRS khi tạo chiến dịch: lịch chạy (3.2.5), template sự kiện (3.2.1.6), giới hạn chạy thử (3.2.1.4). */
class CampaignRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-17T03:00:00Z");

    private static List<String> scheduleErrors(ScheduleDef s, boolean strict) {
        List<FieldViolation> errors = new ArrayList<>();
        CampaignService.validateSchedule(s, strict, NOW, errors);
        return errors.stream().map(v -> v.field() + ":" + v.messageKey()).toList();
    }

    private static ScheduleDef schedule(String type, Instant start, Instant end) {
        return new ScheduleDef(type, start, end, 1, "WEEK", List.of(1), "09:00", null);
    }

    @Test
    void recurringNeedsEndTime() {
        assertThat(scheduleErrors(schedule("RECURRING", NOW.plus(Duration.ofDays(1)), null), false))
                .containsExactly("schedule.endAt:error.validation.endRequired");
        assertThat(scheduleErrors(schedule("ONCE", NOW.plus(Duration.ofDays(1)), null), false)).isEmpty();
    }

    @Test
    void startInPastBlockedOnlyWhenSubmitting() {
        ScheduleDef past = schedule("ONCE", NOW.minus(Duration.ofHours(2)), null);
        assertThat(scheduleErrors(past, true)).containsExactly("schedule.startAt:error.validation.startInPast");
        assertThat(scheduleErrors(past, false)).isEmpty();
        // dung sai 1 phút cho đồng hồ lệch / thời gian người dùng bấm gửi
        assertThat(scheduleErrors(schedule("ONCE", NOW.minusSeconds(30), null), true)).isEmpty();
        assertThat(scheduleErrors(schedule("NOW", NOW.minus(Duration.ofDays(3)), null), true)).isEmpty();
    }

    @Test
    void eventTemplatesAreNormalized() {
        assertThat(EventTemplates.normalize(" sim_activation_d0 ")).isEqualTo("SIM_ACTIVATION_D0");
        assertThat(EventTemplates.exists("birthday")).isTrue();
        assertThat(EventTemplates.exists("UNKNOWN_EVENT")).isFalse();
        assertThat(EventTemplates.normalize("  ")).isNull();
    }

    @Test
    void testLimitDefaultsToTen() {
        CampaignDefinition d = new CampaignDefinition(List.of(), null, null, null);
        assertThat(d.testLimitOrDefault()).isEqualTo(CampaignDefinition.DEFAULT_TEST_LIMIT).isEqualTo(10);
        assertThat(new CampaignDefinition(List.of(), null, null, null, null, 25).testLimitOrDefault()).isEqualTo(25);
    }
}
