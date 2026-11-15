package com.unitel.laoads.domain.campaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.unitel.laoads.domain.pricing.ActionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cấu hình chiến dịch lưu dạng JSON (LADS_CAMPAIGN.DEFINITION_JSON) — đúng shape FE gửi lên:
 * nhóm hành động (A/B + nhóm đối chứng), lịch chạy (khung giờ không gửi), mục tiêu chuyển đổi, ngân sách.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CampaignDefinition(List<ActionGroupDef> actionGroups, ScheduleDef schedule, GoalDef goal, BudgetDef budget) {

    /** Nhóm A/B. SEQUENTIAL: hành động lần lượt, chờ {@code delayMinutes} sau hành động trước (YC 7.0). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionGroupDef(String id, String name, int allocationPercent,
                                 @JsonProperty("isControl") boolean isControl,
                                 String mode, List<ActionDef> actions) {

        public boolean sequential() {
            return !"PARALLEL".equals(mode);
        }
    }

    /** {@code trackLinks}: thay URL trong nội dung bằng link tracking (SRS 3.2.3.6). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionDef(String id, ActionType type, int delayMinutes, String senderId, String subject, String content,
                            String templateId, BigDecimal amount, String packageCode, Boolean trackLinks) {

        public CampaignEnums.Channel channel() {
            return CampaignEnums.Channel.of(type);
        }

        public boolean tracksLinks() {
            return Boolean.TRUE.equals(trackLinks);
        }
    }

    /** type: NOW | ONCE | RECURRING; weekdays 0 = Chủ nhật … 6 = Thứ bảy; sendTime "HH:mm" (giờ Lào). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleDef(String type, Instant startAt, Instant endAt, Integer every, String unit,
                              List<Integer> weekdays, String sendTime, SilentHoursDef silentHours) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SilentHoursDef(boolean enabled, String from, String to) {
    }

    /** mode: AUTO_OCS (so sánh snapshot BI) | MANUAL_FILE (tải danh sách) | NONE. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalDef(String mode, String description, int attributionWindowDays, boolean stopOnGoal,
                          Integer maxNoResponse) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BudgetDef(BigDecimal expected, BigDecimal max) {
    }

    public List<ActionGroupDef> groups() {
        return actionGroups == null ? List.of() : actionGroups;
    }
}
