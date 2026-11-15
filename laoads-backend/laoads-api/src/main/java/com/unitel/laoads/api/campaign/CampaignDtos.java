package com.unitel.laoads.api.campaign;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.BudgetDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.GoalDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.campaign.CampaignEnums;
import com.unitel.laoads.domain.campaign.CampaignHistory;
import com.unitel.laoads.domain.campaign.CampaignInput;
import com.unitel.laoads.domain.common.Times;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** DTO chiến dịch — đúng shape FE (src/api/types.ts: CampaignSummary / CampaignDetail / CampaignInput). */
public final class CampaignDtos {

    private CampaignDtos() {
    }

    public record Summary(Long id, String code, String name, CampaignEnums.Mode mode, CampaignEnums.Type type,
                          CampaignEnums.Status status, Long groupId, String groupName, Long segmentId, String segmentName,
                          int targetCount, List<String> channels, OffsetDateTime startAt, OffsetDateTime endAt,
                          OffsetDateTime createdAt, String createdBy, BigDecimal expectedCost, double sentRate,
                          double openRate, double conversionRate, BigDecimal uplift) {

        public static Summary from(Campaign c, String groupName, String segmentName) {
            int target = Math.max(1, c.getTargetCount());
            return new Summary(c.getId(), c.getCode(), c.getName(), c.getMode(), c.getType(), c.getStatus(), c.getGroupId(),
                    groupName, c.getSegmentId(), segmentName, c.getTargetCount(), CampaignDtos.channels(c),
                    Times.toOffset(c.getStartAt()), Times.toOffset(c.getEndAt()), Times.toOffset(c.getCreatedAt()),
                    c.getCreatedBy(), c.getExpectedCost(), Math.min(1, (double) c.getReachedCount() / target),
                    c.getSentCount() == 0 ? 0 : (double) c.getOpenCount() / c.getSentCount(),
                    Math.min(1, (double) c.getConversionCount() / target), c.getUplift());
        }
    }

    public record HistoryItem(OffsetDateTime at, String actor, String action, String note) {
        static HistoryItem from(CampaignHistory h) {
            return new HistoryItem(Times.toOffset(h.getActionAt()), h.getActor(), h.getAction(), h.getNote());
        }
    }

    public record Detail(Long id, String code, String name, CampaignEnums.Mode mode, CampaignEnums.Type type,
                         CampaignEnums.Status status, Long groupId, String groupName, Long segmentId, String segmentName,
                         int targetCount, List<String> channels, OffsetDateTime startAt, OffsetDateTime endAt,
                         OffsetDateTime createdAt, String createdBy, BigDecimal expectedCost, double sentRate,
                         double openRate, double conversionRate, BigDecimal uplift,
                         String description, String language, List<String> testIsdns, List<ActionGroupDef> actionGroups,
                         ScheduleDef schedule, GoalDef goal, BudgetDef budget, String rejectReason,
                         List<HistoryItem> history) {

        public static Detail from(Campaign c, CampaignDefinition def, String groupName, String segmentName,
                                  List<CampaignHistory> history) {
            Summary s = Summary.from(c, groupName, segmentName);
            BudgetDef budget = new BudgetDef(c.getExpectedCost(), c.getBudgetMax());
            return new Detail(s.id(), s.code(), s.name(), s.mode(), s.type(), s.status(), s.groupId(), s.groupName(),
                    s.segmentId(), s.segmentName(), s.targetCount(), s.channels(), s.startAt(), s.endAt(), s.createdAt(),
                    s.createdBy(), s.expectedCost(), s.sentRate(), s.openRate(), s.conversionRate(), s.uplift(),
                    c.getDescription(), c.getLanguage(),
                    c.getTestIsdns() == null ? List.of() : Arrays.asList(c.getTestIsdns().split(",")),
                    def.groups(), def.schedule(), def.goal(), budget, c.getRejectReason(),
                    history.stream().map(HistoryItem::from).toList());
        }
    }

    /** Body tạo / sửa chiến dịch. targetCount / channels / expectedCost FE gửi kèm bị bỏ qua — BE tự tính. */
    public record Request(@NotBlank @Size(max = 255) String name, @Size(max = 2000) String description,
                          @NotNull CampaignEnums.Mode mode, @NotNull CampaignEnums.Type type, Long groupId,
                          Long segmentId, String language, List<String> testIsdns, List<ActionGroupDef> actionGroups,
                          ScheduleDef schedule, GoalDef goal, BudgetDef budget) {

        public CampaignInput toInput() {
            return new CampaignInput(name, description, mode, type, groupId, segmentId, language, testIsdns,
                    new CampaignDefinition(actionGroups, schedule, goal, budget));
        }
    }

    public record TestSendRequest(Long campaignId, List<String> isdns, List<ActionDef> actions) {
    }

    public record RejectRequest(String reason) {
    }

    public record GroupDto(Long id, String name, long campaignCount, long runningCount, OffsetDateTime createdAt) {
    }

    public record GroupRequest(@NotBlank @Size(max = 255) String name) {
    }

    static List<String> channels(Campaign c) {
        return c.getChannels() == null || c.getChannels().isBlank() ? List.of() : Arrays.asList(c.getChannels().split(","));
    }
}
