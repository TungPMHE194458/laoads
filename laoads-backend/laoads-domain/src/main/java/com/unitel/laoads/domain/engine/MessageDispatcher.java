package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignHistory;
import com.unitel.laoads.domain.campaign.CampaignHistoryRepository;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.campaign.MessageParams;
import com.unitel.laoads.domain.campaign.SchedulePlanner;
import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.settings.NotificationSettings;
import com.unitel.laoads.domain.settings.SenderNumber;
import com.unitel.laoads.domain.settings.SenderNumberRepository;
import com.unitel.laoads.domain.settings.SenderNumberService;
import com.unitel.laoads.domain.settings.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine — gửi các tin tới hạn qua {@link MessageGateway}. Gọi gateway NGOÀI transaction; mỗi tin cập nhật
 * độc lập. Giả định 1 instance chạy job (triển khai hiện tại là 1 container BE).
 */
@Service
@Slf4j
public class MessageDispatcher {

    record Due(long id, long campaignId, long targetId, String isdn, String groupKey, String actionKey,
               ActionType actionType, Channel channel, int attempts, String prevStatus, boolean converted) {
    }

    record CampaignCtx(Campaign campaign, CampaignDefinition def, boolean overBudget) {
    }

    /** Đầu số đang tắt / ngoài phạm vi khi chiến dịch đang chạy: hoãn tin, kiểm tra lại sau khoảng này. */
    static final Duration SENDER_UNAVAILABLE_RETRY = Duration.ofMinutes(10);

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+");
    private static final char[] TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final CampaignRepository campaignRepo;
    private final CampaignHistoryRepository historyRepo;
    private final CampaignService campaignService;
    private final SettingsService settingsService;
    private final SenderNumberRepository senderRepo;
    private final SenderNumberService senderService;
    private final MessageGateway gateway;
    private final MessageParamValues paramValues;
    private final TransactionTemplate tx;
    private final String publicBaseUrl;
    /** Chu kỳ dispatch ~5 giây → bucket chứa TPS × 5 lượt để đạt đúng TPS trung bình. */
    private final SenderThrottle throttle = new SenderThrottle(5.0);

    public MessageDispatcher(JdbcTemplate jdbc, CampaignRepository campaignRepo, CampaignHistoryRepository historyRepo,
                             CampaignService campaignService, SettingsService settingsService,
                             SenderNumberRepository senderRepo, SenderNumberService senderService, MessageGateway gateway,
                             MessageParamValues paramValues, PlatformTransactionManager txManager,
                             @Value("${laoads.engine.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.campaignRepo = campaignRepo;
        this.historyRepo = historyRepo;
        this.campaignService = campaignService;
        this.settingsService = settingsService;
        this.senderRepo = senderRepo;
        this.senderService = senderService;
        this.gateway = gateway;
        this.paramValues = paramValues;
        this.tx = new TransactionTemplate(txManager);
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    /** Gửi tối đa {@code limit} tin tới hạn. Trả về số tin đã xử lý (gửi / bỏ qua / dời lịch). */
    public int dispatch(int limit) {
        Instant now = Instant.now();
        List<Due> due = jdbc.query(
                "SELECT * FROM (SELECT m.ID, m.CAMPAIGN_ID, m.TARGET_ID, m.ISDN, m.GROUP_KEY, m.ACTION_KEY, m.ACTION_TYPE, "
                        + "m.CHANNEL, m.ATTEMPTS, p.STATUS PREV_STATUS, t.CONVERTED "
                        + "FROM LADS_CAMPAIGN_MESSAGE m JOIN LADS_CAMPAIGN c ON c.ID = m.CAMPAIGN_ID "
                        + "JOIN LADS_CAMPAIGN_TARGET t ON t.ID = m.TARGET_ID "
                        + "LEFT JOIN LADS_CAMPAIGN_MESSAGE p ON p.ID = m.PREV_MESSAGE_ID "
                        + "WHERE m.STATUS = 'PENDING' AND m.DUE_AT <= ? AND c.STATUS = 'RUNNING' "
                        + "AND (p.ID IS NULL OR p.STATUS <> 'PENDING') ORDER BY m.DUE_AT, m.ID) WHERE ROWNUM <= ?",
                (rs, i) -> new Due(rs.getLong("ID"), rs.getLong("CAMPAIGN_ID"), rs.getLong("TARGET_ID"), rs.getString("ISDN"),
                        rs.getString("GROUP_KEY"), rs.getString("ACTION_KEY"), ActionType.valueOf(rs.getString("ACTION_TYPE")),
                        Channel.valueOf(rs.getString("CHANNEL")), rs.getInt("ATTEMPTS"), rs.getString("PREV_STATUS"),
                        rs.getInt("CONVERTED") == 1),
                Utc.ts(now), limit);
        if (due.isEmpty()) {
            return 0;
        }
        NotificationSettings settings = settingsService.notification();
        Map<ActionType, BigDecimal> prices = campaignService.unitPrices();
        Map<String, SenderNumber> senders = SenderPolicy.byCode(senderRepo.findAll());
        Map<String, Long> sentToday = senders.values().stream().anyMatch(s -> s.getDailyLimit() != null)
                ? new HashMap<>(senderService.sentToday()) : new HashMap<>();
        Map<String, Integer> overflow = new HashMap<>();
        List<Object[]> throttled = new ArrayList<>();
        Map<Long, CampaignCtx> contexts = new HashMap<>();
        Map<String, Map<String, String>> subscriberValues = subscriberValues(due, contexts);
        Map<Long, Set<String>> unresponsive = unresponsive(due, contexts, now);
        int processed = 0;
        for (Due m : due) {
            CampaignCtx ctx = contexts.computeIfAbsent(m.campaignId(), this::load);
            if (ctx == null || ctx.overBudget()) {
                continue;
            }
            if (m.prevStatus() != null && !"SENT".equals(m.prevStatus()) && !"DELIVERED".equals(m.prevStatus())) {
                processed++;
                skip(m.id(), "Hành động trước trong chuỗi không thành công");
                continue;
            }
            if (m.converted() && ctx.def().goal() != null && ctx.def().goal().stopOnGoal()) {
                processed++;
                skip(m.id(), "Khách hàng đã đạt mục tiêu");
                continue;
            }
            // SRS 3.2.6.1: không phản hồi sau N lần nhận thông báo liên tiếp → bỏ các bước tiếp theo
            if (unresponsive.getOrDefault(m.campaignId(), Set.of()).contains(m.isdn())) {
                processed++;
                skip(m.id(), "Khách hàng không phản hồi sau " + ctx.def().goal().maxNoResponse()
                        + " lần nhận thông báo liên tiếp");
                continue;
            }
            Campaign c = ctx.campaign();
            if (c.getEndAt() != null && now.isAfter(c.getEndAt())) {
                processed++;
                skip(m.id(), "Hết thời gian chiến dịch");
                continue;
            }
            ActionDef action = actionOf(ctx.def(), m.groupKey(), m.actionKey());
            String code = SenderPolicy.key(action == null ? null : action.senderId());
            SenderNumber sender = code == null ? null : senders.get(code);
            if (code != null && sender == null) {
                processed++;
                fail(m.id(), "Đầu số " + code + " không còn tồn tại");
                continue;
            }
            if (sender != null && (!sender.isEnabled() || !sender.allowedFor(c.getWorkspaceId())
                    || !sender.supports(m.channel()))) {
                processed++;
                defer(m.id(), now.plus(SENDER_UNAVAILABLE_RETRY),
                        "Đầu số " + code + " đang tắt hoặc không dùng được cho chiến dịch này — tạm hoãn");
                continue;
            }
            LocalTime[] silent = SenderPolicy.silentWindow(ctx.def(), sender, settings);
            if (silent != null) {
                Instant allowed = SchedulePlanner.applySilentHours(now, silent[0], silent[1]);
                if (allowed.isAfter(now)) {
                    processed++;
                    jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET DUE_AT = ? WHERE ID = ?", Utc.ts(allowed), m.id());
                    continue;
                }
            }
            if (sender != null && sender.getDailyLimit() != null
                    && sentToday.getOrDefault(code, 0L) >= sender.getDailyLimit()) {
                processed++;
                defer(m.id(), Times.nextLaosDayStart(now),
                        "Đạt giới hạn " + sender.getDailyLimit() + " tin/ngày của đầu số " + code);
                continue;
            }
            if (sender != null && sender.getGwTps() != null
                    && !throttle.tryAcquire(code, sender.getGwTps(), System.nanoTime())) {
                // hết lượt TPS: rải lại DUE_AT theo TPS, không tính là đã xử lý để vòng dispatch dừng
                int idx = overflow.merge(code, 1, Integer::sum) - 1;
                throttled.add(new Object[]{Utc.ts(SenderPolicy.throttledDueAt(now, idx, sender.getGwTps())), m.id()});
                continue;
            }
            processed++;
            // bảng giá theo đơn vị tính: cộng tài nguyên = đơn giá × số lượng
            BigDecimal unit = prices.getOrDefault(m.actionType(), BigDecimal.ZERO);
            BigDecimal cost = action == null ? unit : CampaignService.costPerTarget(action, unit);
            if (send(m, ctx, action, sender, code, settings, cost, subscriberValues.get(m.isdn()), now) && code != null) {
                sentToday.merge(code, 1L, Long::sum);
            }
        }
        if (!throttled.isEmpty()) {
            jdbc.batchUpdate("UPDATE LADS_CAMPAIGN_MESSAGE SET DUE_AT = ? WHERE ID = ? AND STATUS = 'PENDING'", throttled);
        }
        return processed;
    }

    /**
     * Giá trị tham số nguồn LADS_SUBSCRIBER cho các tin trong lô — một truy vấn cho cả lô, chỉ với thuê bao có
     * hành động dùng tham số loại này.
     */
    private Map<String, Map<String, String>> subscriberValues(List<Due> due, Map<Long, CampaignCtx> contexts) {
        Set<String> isdns = new HashSet<>();
        for (Due m : due) {
            CampaignCtx ctx = contexts.computeIfAbsent(m.campaignId(), this::load);
            ActionDef a = ctx == null || ctx.overBudget() ? null : actionOf(ctx.def(), m.groupKey(), m.actionKey());
            if (a != null && MessageParams.needsSubscriber(a.subject(), a.content())) {
                isdns.add(m.isdn());
            }
        }
        return isdns.isEmpty() ? Map.of() : paramValues.load(isdns);
    }

    /** ISDN không phản hồi theo từng chiến dịch có bật điều kiện dừng {@code maxNoResponse} (một truy vấn / chiến dịch). */
    private Map<Long, Set<String>> unresponsive(List<Due> due, Map<Long, CampaignCtx> contexts, Instant now) {
        Map<Long, List<String>> byCampaign = new HashMap<>();
        for (Due m : due) {
            CampaignCtx ctx = contexts.computeIfAbsent(m.campaignId(), this::load);
            if (ctx != null && ctx.def().goal() != null && ctx.def().goal().tracksNoResponse()) {
                byCampaign.computeIfAbsent(m.campaignId(), k -> new ArrayList<>()).add(m.isdn());
            }
        }
        Map<Long, Set<String>> out = new HashMap<>();
        byCampaign.forEach((cid, isdns) -> out.put(cid,
                StopConditions.unresponsiveAmong(jdbc, contexts.get(cid).def().goal(), cid, isdns, now)));
        return out;
    }

    /** @return true nếu gateway nhận tin (SENT / DELIVERED) */
    private boolean send(Due m, CampaignCtx ctx, ActionDef action, SenderNumber sender, String senderCode,
                         NotificationSettings settings, BigDecimal unitPrice, Map<String, String> subscriber,
                         Instant now) {
        // thay tham số trước, rồi mới thay link — URL có tham số ({{isdn}}) được tracking với giá trị thật
        String content = action == null ? null : MessageParams.render(action.content(), m.isdn(), subscriber);
        String subject = action == null ? null : MessageParams.render(action.subject(), m.isdn(), subscriber);
        if (action != null && action.tracksLinks() && content != null) {
            content = trackLinks(content, m.id(), m.campaignId(), now);
        }
        boolean waTemplate = action != null && action.usesWhatsappTemplate();
        MessageGateway.Outbound out = new MessageGateway.Outbound(m.id(), m.channel(), m.actionType(), m.isdn(),
                action == null ? null : action.senderId(), subject, waTemplate ? null : content,
                action == null ? null : action.amount(), action == null ? null : action.packageCode(),
                ctx.campaign().getLanguage(), waTemplate ? action.templateId() : null);
        MessageGateway.Result r;
        try {
            r = gateway.send(out);
        } catch (RuntimeException e) {
            r = MessageGateway.Result.fail("Lỗi gateway: " + e.getMessage(), true);
        }
        int attempts = m.attempts() + 1;
        if (r.success()) {
            jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = ?, ATTEMPTS = ?, SENT_AT = ?, DELIVERED_AT = ?, COST = ?, "
                            + "PROVIDER_REF = ?, LAST_ERROR = NULL, SENDER_CODE = ? WHERE ID = ?",
                    r.delivered() ? "DELIVERED" : "SENT", attempts, Utc.ts(now), r.delivered() ? Utc.ts(now) : null,
                    unitPrice, r.providerRef(), senderCode, m.id());
            return true;
        }
        if (r.retryable() && attempts <= SenderPolicy.retryMaxAttempts(sender, settings)) {
            Instant retryAt = now.plus(Duration.ofMinutes(SenderPolicy.retryIntervalMinutes(sender, settings)));
            jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET ATTEMPTS = ?, DUE_AT = ?, LAST_ERROR = ?, SENDER_CODE = ? WHERE ID = ?",
                    attempts, Utc.ts(retryAt), truncate(r.error()), senderCode, m.id());
        } else {
            boolean refund = settings.refundOnFailure() && m.channel() == Channel.OCS;
            jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = 'FAILED', ATTEMPTS = ?, LAST_ERROR = ?, REFUNDED = ?, "
                    + "COST = 0, SENDER_CODE = ? WHERE ID = ?", attempts, truncate(r.error()), refund ? 1 : 0, senderCode, m.id());
        }
        return false;
    }

    private CampaignCtx load(long campaignId) {
        Campaign c = campaignRepo.findById(campaignId).orElse(null);
        if (c == null || !c.is(Status.RUNNING)) {
            return null;
        }
        CampaignDefinition def = campaignService.definitionOf(c);
        boolean over = false;
        if (c.getBudgetMax() != null && c.getBudgetMax().signum() > 0) {
            BigDecimal spent = jdbc.queryForObject("SELECT NVL(SUM(COST), 0) FROM LADS_CAMPAIGN_MESSAGE WHERE CAMPAIGN_ID = ?",
                    BigDecimal.class, campaignId);
            if (spent != null && spent.compareTo(c.getBudgetMax()) >= 0) {
                over = true;
                autoPause(campaignId, "Đạt ngân sách tối đa " + c.getBudgetMax().toPlainString() + " LAK");
            }
        }
        return new CampaignCtx(c, def, over);
    }

    private void autoPause(long campaignId, String reason) {
        tx.executeWithoutResult(s -> campaignRepo.findById(campaignId).ifPresent(c -> {
            if (c.is(Status.RUNNING)) {
                c.setStatus(Status.PAUSED);
                c.setUpdatedAt(Instant.now());
                historyRepo.save(CampaignHistory.of(campaignId, "system", "PAUSE", reason));
                log.info("[ENGINE] Tự tạm dừng chiến dịch #{}: {}", campaignId, reason);
            }
        }));
    }

    private void defer(long messageId, Instant dueAt, String reason) {
        jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET DUE_AT = ?, LAST_ERROR = ? WHERE ID = ? AND STATUS = 'PENDING'",
                Utc.ts(dueAt), truncate(reason), messageId);
    }

    private void fail(long messageId, String reason) {
        jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = 'FAILED', LAST_ERROR = ?, COST = 0 "
                + "WHERE ID = ? AND STATUS = 'PENDING'", truncate(reason), messageId);
    }

    private void skip(long messageId, String reason) {
        jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = 'SKIPPED', LAST_ERROR = ? WHERE ID = ? AND STATUS = 'PENDING'",
                reason, messageId);
    }

    static ActionDef actionOf(CampaignDefinition def, String groupKey, String actionKey) {
        List<ActionGroupDef> groups = def.groups();
        for (int gi = 0; gi < groups.size(); gi++) {
            ActionGroupDef g = groups.get(gi);
            String gk = g.id() == null || g.id().isBlank() ? "g" + gi : g.id();
            if (!gk.equals(groupKey) || g.actions() == null) {
                continue;
            }
            for (int ai = 0; ai < g.actions().size(); ai++) {
                ActionDef a = g.actions().get(ai);
                String ak = a.id() == null || a.id().isBlank() ? "a" + ai : a.id();
                if (ak.equals(actionKey)) {
                    return a;
                }
            }
        }
        return null;
    }

    /** Thay mỗi URL bằng link tracking /api/v1/t/{token} (SRS 3.2.3.6). */
    String trackLinks(String content, long messageId, long campaignId, Instant now) {
        Matcher m = URL.matcher(content);
        StringBuilder sb = new StringBuilder();
        List<Object[]> rows = new ArrayList<>();
        while (m.find()) {
            String url = m.group();
            if (url.startsWith(publicBaseUrl + "/api/v1/t/") || url.length() > 2000) {
                m.appendReplacement(sb, Matcher.quoteReplacement(url));
                continue;
            }
            String token = token();
            rows.add(new Object[]{token, messageId, campaignId, url, Utc.ts(now)});
            m.appendReplacement(sb, Matcher.quoteReplacement(publicBaseUrl + "/api/v1/t/" + token));
        }
        m.appendTail(sb);
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO LADS_TRACKING_LINK (TOKEN, MESSAGE_ID, CAMPAIGN_ID, TARGET_URL, CLICK_COUNT, "
                    + "CREATED_AT) VALUES (?, ?, ?, ?, 0, ?)", rows);
        }
        return sb.toString();
    }

    private String token() {
        char[] c = new char[12];
        for (int i = 0; i < c.length; i++) {
            c[i] = TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)];
        }
        return new String(c);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
