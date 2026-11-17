package com.unitel.laoads.domain.campaign;

import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.pricing.ActionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageParamsTest {

    private static final String ISDN = "8562055512345";

    @Test
    void rendersTargetAndSubscriberValues() {
        String out = MessageParams.render("Số {{isdn}} đang dùng gói {{package}}.", ISDN, Map.of("package", "M50"));
        assertThat(out).isEqualTo("Số 8562055512345 đang dùng gói M50.");
    }

    @Test
    void missingValueFallsBackToDefaultThenEmpty() {
        assertThat(MessageParams.render("Gói: {{package|gói mới}}", ISDN, null)).isEqualTo("Gói: gói mới");
        assertThat(MessageParams.render("Gói: {{ package | gói mới }}", ISDN, Map.of())).isEqualTo("Gói: gói mới");
        java.util.HashMap<String, String> blank = new java.util.HashMap<>();
        blank.put("package", null);
        assertThat(MessageParams.render("Gói: [{{package}}]", ISDN, blank)).isEqualTo("Gói: []");
    }

    @Test
    void keyIsCaseInsensitiveAndReplacementIsLiteral() {
        assertThat(MessageParams.render("{{ISDN}} {{Package}}", ISDN, Map.of("package", "$1\\x")))
                .isEqualTo("8562055512345 $1\\x");
    }

    @Test
    void textWithoutParamsIsReturnedAsIs() {
        assertThat(MessageParams.render("Không có tham số {x} {{", ISDN, null)).isEqualTo("Không có tham số {x} {{");
        assertThat(MessageParams.render(null, ISDN, null)).isNull();
    }

    @Test
    void unknownTokensKeepUserSpellingAndOrder() {
        assertThat(MessageParams.unknownTokens("Chào {{name}}, {{isdn}}, {{link}}, lại {{name}}"))
                .containsExactly("{{name}}", "{{link}}");
        assertThat(MessageParams.unknownTokens("{{isdn}} {{package|x}}")).isEmpty();
        // tham số lạ lúc gửi (chiến dịch cũ) → mặc định hoặc rỗng, không gửi nguyên chữ cho khách
        assertThat(MessageParams.render("Chào {{name|quý khách}}{{link}}!", ISDN, null)).isEqualTo("Chào quý khách!");
    }

    @Test
    void needsSubscriberOnlyForSubscriberParams() {
        assertThat(MessageParams.needsSubscriber("Số {{isdn}}", null)).isFalse();
        assertThat(MessageParams.needsSubscriber(null, "Gói {{package|mới}}")).isTrue();
    }

    @Test
    void submitValidationReportsUnknownParamsPerField() {
        ActionDef email = new ActionDef("a1", ActionType.NOTIFICATION_EMAIL, 0, null, "Chào {{name}}",
                "Gói {{package}} {{link}}", null, null, null, false);
        ActionDef ok = new ActionDef("a2", ActionType.NOTIFICATION_SMS, 0, "118", null, "Số {{isdn}}", null, null, null, false);
        CampaignDefinition def = new CampaignDefinition(List.of(
                new ActionGroupDef("g1", "A", 90, false, "SEQUENTIAL", List.of(email, ok)),
                new ActionGroupDef("g2", "Đối chứng", 10, true, "SEQUENTIAL", List.of())), null, null, null);
        List<FieldViolation> errors = new ArrayList<>();
        CampaignService.validateParams(def, errors);
        assertThat(errors).extracting(FieldViolation::field)
                .containsExactly("actionGroups[0].actions[0].subject", "actionGroups[0].actions[0].content");
        assertThat(errors.get(1).messageKey()).isEqualTo("error.validation.unknownParam");
        assertThat(errors.get(1).args()).containsExactly("{{link}}");
    }
}
