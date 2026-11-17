package com.unitel.laoads.domain.campaign;

import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.FrameDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.GoalDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.pricing.ActionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    void customerLimitDefaultsToTenForTestAndIsOptionalForOfficial() {
        CampaignDefinition d = new CampaignDefinition(List.of(), null, null, null);
        assertThat(d.limitFor(CampaignEnums.Mode.TEST)).isEqualTo(CampaignDefinition.DEFAULT_TEST_LIMIT).isEqualTo(10);
        assertThat(d.limitFor(CampaignEnums.Mode.OFFICIAL)).isNull();
        CampaignDefinition limited = new CampaignDefinition(List.of(), null, null, null, null, 25);
        assertThat(limited.limitFor(CampaignEnums.Mode.TEST)).isEqualTo(25);
        assertThat(limited.limitFor(CampaignEnums.Mode.OFFICIAL)).isEqualTo(25);
    }

    private static ActionDef action(ActionType type, String amount) {
        return new ActionDef("a", type, 0, null, null, null, null, amount == null ? null : new BigDecimal(amount), null, null);
    }

    @Test
    void telecomActionsWithAmountArePricedPerUnit() {
        BigDecimal unit = new BigDecimal("120");
        // như bảng giá laoads.com: "trên mỗi MB được cộng" → đơn giá × số MB
        assertThat(CampaignService.costPerTarget(action(ActionType.ADD_DATA, "100"), unit)).isEqualByComparingTo("12000");
        assertThat(CampaignService.costPerTarget(action(ActionType.ADD_MONEY, "5000"), new BigDecimal("0.1"))).isEqualByComparingTo("500");
        assertThat(CampaignService.costPerTarget(action(ActionType.ADD_DATA, null), unit)).isEqualByComparingTo("0");
        // tin thông báo và đăng ký gói tính theo lượt, bỏ qua amount
        assertThat(CampaignService.costPerTarget(action(ActionType.NOTIFICATION_SMS, null), new BigDecimal("35"))).isEqualByComparingTo("35");
        assertThat(CampaignService.costPerTarget(action(ActionType.ADD_PACKAGE, "3"), new BigDecimal("500"))).isEqualByComparingTo("500");
        assertThat(CampaignService.costPerTarget(action(ActionType.ADD_DATA, "100"), null)).isEqualByComparingTo("0");
    }

    @Test
    void whatsappTemplateModeOnlyAppliesToWhatsappActions() {
        ActionDef wa = new ActionDef("a", ActionType.NOTIFICATION_WHATSAPP, 0, null, null, null, "wa-tpl-0001", null, null, null, "TEMPLATE");
        ActionDef waContent = new ActionDef("a", ActionType.NOTIFICATION_WHATSAPP, 0, null, null, "Xin chào", null, null, null, null, "CONTENT");
        ActionDef sms = new ActionDef("a", ActionType.NOTIFICATION_SMS, 0, null, null, "Xin chào", null, null, null, null, "TEMPLATE");
        assertThat(wa.usesWhatsappTemplate()).isTrue();
        assertThat(waContent.usesWhatsappTemplate()).isFalse();
        assertThat(sms.usesWhatsappTemplate()).isFalse();
    }

    @Test
    void scheduleFramesAreValidatedOneByOneAndCapped() {
        Instant start = NOW.plus(Duration.ofDays(1));
        Instant end = NOW.plus(Duration.ofDays(30));
        FrameDef ok = new FrameDef(1, "WEEK", List.of(1), null, "09:00");
        FrameDef bad = new FrameDef(1, "WEEK", List.of(1), null, "9h");
        ScheduleDef two = new ScheduleDef("RECURRING", start, end, 1, "WEEK", List.of(1), "09:00", null, null, null, List.of(ok, bad));
        assertThat(scheduleErrors(two, false)).containsExactly("schedule.frames[1]:error.validation.recurringRule");
        ScheduleDef eleven = new ScheduleDef("RECURRING", start, end, 1, "WEEK", List.of(1), "09:00", null, null, null,
                java.util.Collections.nCopies(11, ok));
        assertThat(scheduleErrors(eleven, false)).contains("schedule.frames:error.validation.range");
    }

    @Test
    void outcomesDefaultToNotAchievedOnlyWhenThereIsAGoal() {
        assertThat(new GoalDef("AUTO_OCS", "", 7, true, null).outcomeList()).containsExactly("GOAL_NOT_ACHIEVED");
        assertThat(new GoalDef("NONE", "", 7, true, null).outcomeList()).isEmpty();
        assertThat(new GoalDef("NONE", "", 7, true, null, null, null, List.of("WHATSAPP_FAILED")).outcomeList())
                .containsExactly("WHATSAPP_FAILED");
        assertThat(new GoalDef("AUTO_OCS", "", 7, true, null, null, null, List.of()).outcomeList()).isEmpty();
    }
}
