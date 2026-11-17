package com.unitel.laoads.domain.campaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.segment.SegmentCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cấu hình chiến dịch lưu dạng JSON (LADS_CAMPAIGN.DEFINITION_JSON) — đúng shape FE gửi lên:
 * nhóm hành động (A/B + nhóm đối chứng), lịch chạy (khung giờ không gửi), mục tiêu chuyển đổi, ngân sách.
 * {@code eventTemplate}: template sự kiện của chiến dịch EVENT (SRS 3.2.1.6, danh mục {@link EventTemplates});
 * {@code testLimit}: giới hạn số khách hàng chạy thử của chiến dịch TEST (SRS 3.2.1.4, mặc định 10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CampaignDefinition(List<ActionGroupDef> actionGroups, ScheduleDef schedule, GoalDef goal, BudgetDef budget,
                                 String eventTemplate, Integer testLimit) {

    public static final int DEFAULT_TEST_LIMIT = 10;
    public static final int MAX_TEST_LIMIT = 1000;

    public CampaignDefinition(List<ActionGroupDef> actionGroups, ScheduleDef schedule, GoalDef goal, BudgetDef budget) {
        this(actionGroups, schedule, goal, budget, null, null);
    }

    public int testLimitOrDefault() {
        return testLimit == null ? DEFAULT_TEST_LIMIT : testLimit;
    }

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

    /**
     * type: NOW | ONCE | RECURRING; weekdays 0 = Chủ nhật … 6 = Thứ bảy; sendTime "HH:mm" (giờ Lào).
     * SRS 3.2.5.3 (cài đặt nâng cao): {@code monthDays} các ngày trong tháng khi unit = MONTH (1–31, trống = ngày của
     * startAt; ngày lớn hơn số ngày của tháng chạy vào ngày cuối tháng); {@code maxOccurrences} hoàn thành sau N lần lặp.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleDef(String type, Instant startAt, Instant endAt, Integer every, String unit,
                              List<Integer> weekdays, String sendTime, SilentHoursDef silentHours,
                              List<Integer> monthDays, Integer maxOccurrences) {

        public ScheduleDef(String type, Instant startAt, Instant endAt, Integer every, String unit,
                           List<Integer> weekdays, String sendTime, SilentHoursDef silentHours) {
            this(type, startAt, endAt, every, unit, weekdays, sendTime, silentHours, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SilentHoursDef(boolean enabled, String from, String to) {
    }

    /**
     * mode: AUTO_OCS (so sánh snapshot BI) | MANUAL_FILE (tải danh sách) | NONE.
     * Điều kiện dừng (SRS 3.2.6.1 #15) — loại khách hàng khỏi các chu kỳ / bước tiếp theo nếu:
     * {@code stopOnGoal} đã đạt mục tiêu; {@code maxNoResponse} (null = tắt) nhận N thông báo liên tiếp không phản hồi
     * (không mở, không click). {@code reentryDays} (null = tắt): khách hàng bị loại được quay lại sau N ngày.
     * {@code conditions} (SRS 3.2.6.1 #5–#13): điều kiện đạt mục tiêu trên dữ liệu BI (bảng all_report_stl_new →
     * LADS_SUBSCRIBER), cùng danh mục thuộc tính / toán tử với tệp khách hàng; bắt buộc khi gửi duyệt mục tiêu AUTO_OCS.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalDef(String mode, String description, int attributionWindowDays, boolean stopOnGoal,
                          Integer maxNoResponse, Integer reentryDays, List<SegmentCondition> conditions) {

        public GoalDef(String mode, String description, int attributionWindowDays, boolean stopOnGoal,
                       Integer maxNoResponse) {
            this(mode, description, attributionWindowDays, stopOnGoal, maxNoResponse, null, null);
        }

        public List<SegmentCondition> conditionList() {
            return conditions == null ? List.of() : conditions;
        }

        public boolean tracksNoResponse() {
            return maxNoResponse != null && maxNoResponse > 0;
        }

        public boolean excludesAnyone() {
            return stopOnGoal || tracksNoResponse();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BudgetDef(BigDecimal expected, BigDecimal max) {
    }

    public List<ActionGroupDef> groups() {
        return actionGroups == null ? List.of() : actionGroups;
    }

    /** Có hành động nào gửi bằng đầu số này không (so khớp mã, không phân biệt hoa/thường). */
    public boolean usesSender(String senderCode) {
        if (senderCode == null) {
            return false;
        }
        return groups().stream()
                .filter(g -> g.actions() != null)
                .flatMap(g -> g.actions().stream())
                .anyMatch(a -> a.senderId() != null && a.senderId().trim().equalsIgnoreCase(senderCode.trim()));
    }
}
