package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.campaign.MessageParams;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.Isdns;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.pricing.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bắn tin thử (YC nâng cấp 6.0): gửi ngay nội dung các hành động truyền thông tới danh sách ISDN bất kỳ,
 * không ghi vào số liệu chiến dịch. Hành động lấy theo thứ tự: bản nháp FE gửi lên → chiến dịch đã lưu →
 * một tin SMS mẫu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestSendService {

    public record Result(String isdn, String channel, String status, String detail) {
    }

    static final int MAX_ISDNS = 20;

    private final MessageGateway gateway;
    private final CampaignService campaignService;
    private final MessageParamValues paramValues;

    public List<Result> send(long workspaceId, Long campaignId, List<String> rawIsdns, List<ActionDef> draftActions,
                             String actor) {
        Set<String> isdns = new LinkedHashSet<>();
        for (String r : rawIsdns == null ? List.<String>of() : rawIsdns) {
            if (r == null || r.isBlank()) {
                continue;
            }
            isdns.add(Isdns.normalize(r).orElseThrow(() -> LaoadsException.validation(List.of(new FieldViolation(
                    "isdns", "error.validation.isdnInvalid", "Số thuê bao không hợp lệ", r)))));
        }
        if (isdns.isEmpty() || isdns.size() > MAX_ISDNS) {
            throw LaoadsException.validation(List.of(new FieldViolation("isdns", "error.validation.range",
                    "Giá trị phải từ 1 đến " + MAX_ISDNS, 1, MAX_ISDNS)));
        }
        List<ActionDef> actions = notificationActions(draftActions);
        String language = "LO";
        if (actions.isEmpty() && campaignId != null) {
            Campaign c = campaignService.get(workspaceId, campaignId);
            language = c.getLanguage();
            for (ActionGroupDef g : campaignService.definitionOf(c).groups()) {
                if (!g.isControl() && g.actions() != null) {
                    actions = notificationActions(g.actions());
                    break;
                }
            }
        }
        if (actions.isEmpty()) {
            actions = List.of(new ActionDef("test", ActionType.NOTIFICATION_SMS, 0, null, null,
                    "[LaoAds] Tin nhắn thử nghiệm", null, null, null, false));
        }
        // tham số nội dung thay như khi gửi thật: số thử không có trong LADS_SUBSCRIBER → giá trị mặc định / rỗng
        boolean needsSubscriber = actions.stream().anyMatch(a -> MessageParams.needsSubscriber(a.subject(), a.content()));
        Map<String, Map<String, String>> values = needsSubscriber ? paramValues.load(isdns) : Map.of();
        List<Result> out = new ArrayList<>();
        for (String isdn : isdns) {
            for (ActionDef a : actions) {
                MessageGateway.Result r;
                try {
                    r = gateway.send(new MessageGateway.Outbound(0, a.channel(), a.type(), isdn, a.senderId(),
                            MessageParams.render(a.subject(), isdn, values.get(isdn)),
                            a.usesWhatsappTemplate() ? null : MessageParams.render(a.content(), isdn, values.get(isdn)),
                            a.amount(), a.packageCode(), language, a.usesWhatsappTemplate() ? a.templateId() : null));
                } catch (RuntimeException e) {
                    r = MessageGateway.Result.fail(e.getMessage(), false);
                }
                out.add(new Result(isdn, a.channel().name(), r.success() ? "SENT" : "FAILED",
                        r.success() ? "Đã gửi tới " + gateway.name() + (r.providerRef() == null ? "" : " (" + r.providerRef() + ")")
                                : r.error()));
            }
        }
        log.info("[TEST-SEND] {} bắn thử {} tin tới {} số (chiến dịch {})", actor, out.size(), isdns.size(), campaignId);
        return out;
    }

    private static List<ActionDef> notificationActions(List<ActionDef> actions) {
        if (actions == null) {
            return List.of();
        }
        return actions.stream().filter(a -> a.type() != null && a.channel() != Channel.OCS).toList();
    }
}
