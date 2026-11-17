package com.unitel.laoads.domain.campaign;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.segment.SegmentCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Cấu hình chiến dịch lưu dạng JSON (LADS_CAMPAIGN.DEFINITION_JSON) — đúng shape FE gửi lên:
 * nhóm hành động (A/B + nhóm đối chứng), lịch chạy (khung giờ không gửi), mục tiêu chuyển đổi, ngân sách.
 * {@code eventTemplate}: template sự kiện của chiến dịch EVENT (SRS 3.2.1.6, danh mục {@link EventTemplates});
 * {@code limit}: giới hạn số lượng khách hàng (như laoads.com) — TEST bắt buộc, mặc định 10 (SRS 3.2.1.4);
 * OFFICIAL tuỳ chọn, null = toàn bộ tệp. JSON cũ dùng tên {@code testLimit}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CampaignDefinition(List<ActionGroupDef> actionGroups, ScheduleDef schedule, GoalDef goal, BudgetDef budget,
                                 String eventTemplate, @JsonAlias("testLimit") Integer limit) {

    public static final int DEFAULT_TEST_LIMIT = 10;
    public static final int MAX_TEST_LIMIT = 1000;

    public CampaignDefinition(List<ActionGroupDef> actionGroups, ScheduleDef schedule, GoalDef goal, BudgetDef budget) {
        this(actionGroups, schedule, goal, budget, null, null);
    }

    /** Số khách hàng tối đa của lượt chạy: TEST mặc định 10; OFFICIAL null = không giới hạn. */
    public Integer limitFor(CampaignEnums.Mode mode) {
        return mode == CampaignEnums.Mode.TEST ? Integer.valueOf(limit == null ? DEFAULT_TEST_LIMIT : limit) : limit;
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

    /**
     * {@code trackLinks}: thay URL trong nội dung bằng link tracking (SRS 3.2.3.6). {@code senderId}: mã đầu số (SMS /
     * USSD) hoặc brand WhatsApp. WhatsApp có 2 chế độ như laoads.com: {@code whatsappMode} = CONTENT (nội dung tự do)
     * hoặc TEMPLATE ({@code templateId} = id template đã được duyệt ở nhà cung cấp).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionDef(String id, ActionType type, int delayMinutes, String senderId, String subject, String content,
                            String templateId, BigDecimal amount, String packageCode, Boolean trackLinks,
                            String whatsappMode) {

        public ActionDef(String id, ActionType type, int delayMinutes, String senderId, String subject, String content,
                         String templateId, BigDecimal amount, String packageCode, Boolean trackLinks) {
            this(id, type, delayMinutes, senderId, subject, content, templateId, amount, packageCode, trackLinks, null);
        }

        public CampaignEnums.Channel channel() {
            return CampaignEnums.Channel.of(type);
        }

        public boolean tracksLinks() {
            return Boolean.TRUE.equals(trackLinks);
        }

        /** WhatsApp gửi bằng template (không dùng nội dung tự do). */
        public boolean usesWhatsappTemplate() {
            return type == ActionType.NOTIFICATION_WHATSAPP && "TEMPLATE".equals(whatsappMode);
        }
    }

    /**
     * type: NOW | ONCE | RECURRING; weekdays 0 = Chủ nhật … 6 = Thứ bảy; sendTime "HH:mm" (giờ Lào).
     * SRS 3.2.5.3 (cài đặt nâng cao): {@code monthDays} các ngày trong tháng khi unit = MONTH (1–31, trống = ngày của
     * startAt; ngày lớn hơn số ngày của tháng chạy vào ngày cuối tháng); {@code maxOccurrences} hoàn thành sau N lần lặp.
     * {@code frames}: nhiều khung thời gian ("Khung thời gian 1, 2…" như laoads.com) — có thì thay cho
     * every / unit / weekdays / monthDays / sendTime ở cấp ngoài; lượt kế tiếp = sớm nhất trong các khung.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleDef(String type, Instant startAt, Instant endAt, Integer every, String unit,
                              List<Integer> weekdays, String sendTime, SilentHoursDef silentHours,
                              List<Integer> monthDays, Integer maxOccurrences, List<FrameDef> frames) {

        public ScheduleDef(String type, Instant startAt, Instant endAt, Integer every, String unit,
                           List<Integer> weekdays, String sendTime, SilentHoursDef silentHours) {
            this(type, startAt, endAt, every, unit, weekdays, sendTime, silentHours, null, null, null);
        }

        public ScheduleDef(String type, Instant startAt, Instant endAt, Integer every, String unit,
                           List<Integer> weekdays, String sendTime, SilentHoursDef silentHours,
                           List<Integer> monthDays, Integer maxOccurrences) {
            this(type, startAt, endAt, every, unit, weekdays, sendTime, silentHours, monthDays, maxOccurrences, null);
        }

        /** Các khung thời gian hiệu lực: {@code frames} nếu có, không thì một khung từ các trường cấp ngoài. */
        public List<FrameDef> effectiveFrames() {
            return frames != null && !frames.isEmpty() ? frames
                    : List.of(new FrameDef(every, unit, weekdays, monthDays, sendTime));
        }
    }

    /** Một khung thời gian của lịch lặp: mỗi {@code every} {@code unit}, vào giờ {@code sendTime}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FrameDef(Integer every, String unit, List<Integer> weekdays, List<Integer> monthDays, String sendTime) {
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
     * {@code outcomes} ("Trích xuất khách hàng" của laoads.com): khi chiến dịch hoàn tất, tự tạo tệp khách hàng cho từng
     * nhóm đã chọn — {@link #OUTCOMES}. null (dữ liệu cũ) = chỉ GOAL_NOT_ACHIEVED khi có mục tiêu.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalDef(String mode, String description, int attributionWindowDays, boolean stopOnGoal,
                          Integer maxNoResponse, Integer reentryDays, List<SegmentCondition> conditions,
                          List<String> outcomes) {

        public static final Set<String> OUTCOMES = Set.of("GOAL_ACHIEVED", "GOAL_NOT_ACHIEVED", "WHATSAPP_FAILED");

        public GoalDef(String mode, String description, int attributionWindowDays, boolean stopOnGoal,
                       Integer maxNoResponse) {
            this(mode, description, attributionWindowDays, stopOnGoal, maxNoResponse, null, null, null);
        }

        public GoalDef(String mode, String description, int attributionWindowDays, boolean stopOnGoal,
                       Integer maxNoResponse, Integer reentryDays, List<SegmentCondition> conditions) {
            this(mode, description, attributionWindowDays, stopOnGoal, maxNoResponse, reentryDays, conditions, null);
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

        public boolean hasGoal() {
            return mode != null && !"NONE".equals(mode);
        }

        /** Nhóm khách hàng cần trích xuất khi hoàn tất (giữ hành vi cũ khi chưa cấu hình). */
        public List<String> outcomeList() {
            if (outcomes != null) {
                return outcomes;
            }
            return hasGoal() ? List.of("GOAL_NOT_ACHIEVED") : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BudgetDef(BigDecimal expected, BigDecimal max) {
    }

    public List<ActionGroupDef> groups() {
        return actionGroups == null ? List.of() : actionGroups;
    }

    /** Có hành động nào gửi bằng đầu số / brand này không (so khớp mã, không phân biệt hoa/thường). */
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
