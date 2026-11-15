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
import com.unitel.laoads.domain.campaign.SchedulePlanner;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.settings.NotificationSettings;
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
import java.util.List;
import java.util.Map;
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

    record CampaignCtx(Campaign campaign, CampaignDefinition def, LocalTime[] silent, boolean overBudget) {
    }

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+");
    private static final char[] TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final CampaignRepository campaignRepo;
    private final CampaignHistoryRepository historyRepo;
    private final CampaignService campaignService;
    private final SettingsService settingsService;
    private final MessageGateway gateway;
    private final TransactionTemplate tx;
    private final String publicBaseUrl;

    public MessageDispatcher(JdbcTemplate jdbc, CampaignRepository campaignRepo, CampaignHistoryRepository historyRepo,
                             CampaignService campaignService, SettingsService settingsService, MessageGateway gateway,
                             PlatformTransactionManager txManager,
                             @Value("${laoads.engine.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.campaignRepo = campaignRepo;
        this.historyRepo = historyRepo;
        this.campaignService = campaignService;
        this.settingsService = settingsService;
        this.gateway = gateway;
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
        Map<Long, CampaignCtx> contexts = new HashMap<>();
        int processed = 0;
        for (Due m : due) {
            CampaignCtx ctx = contexts.computeIfAbsent(m.campaignId(), id -> load(id, settings));
            if (ctx == null || ctx.overBudget()) {
                continue;
            }
            processed++;
            if (m.prevStatus() != null && !"SENT".equals(m.prevStatus()) && !"DELIVERED".equals(m.prevStatus())) {
                skip(m.id(), "Hành động trước trong chuỗi không thành công");
                continue;
            }
            if (m.converted() && ctx.def().goal() != null && ctx.def().goal().stopOnGoal()) {
                skip(m.id(), "Khách hàng đã đạt mục tiêu");
                continue;
            }
            Campaign c = ctx.campaign();
            if (c.getEndAt() != null && now.isAfter(c.getEndAt())) {
                skip(m.id(), "Hết thời gian chiến dịch");
                continue;
            }
            if (ctx.silent() != null) {
                Instant allowed = SchedulePlanner.applySilentHours(now, ctx.silent()[0], ctx.silent()[1]);
                if (allowed.isAfter(now)) {
                    jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET DUE_AT = ? WHERE ID = ?", Utc.ts(allowed), m.id());
                    continue;
                }
            }
            send(m, ctx, settings, prices.getOrDefault(m.actionType(), BigDecimal.ZERO), now);
        }
        return processed;
    }

    private void send(Due m, CampaignCtx ctx, NotificationSettings settings, BigDecimal unitPrice, Instant now) {
        ActionDef action = actionOf(ctx.def(), m.groupKey(), m.actionKey());
        String content = action == null ? null : action.content();
        if (action != null && action.tracksLinks() && content != null) {
            content = trackLinks(content, m.id(), m.campaignId(), now);
        }
        MessageGateway.Outbound out = new MessageGateway.Outbound(m.id(), m.channel(), m.actionType(), m.isdn(),
                action == null ? null : action.senderId(), action == null ? null : action.subject(), content,
                action == null ? null : action.amount(), action == null ? null : action.packageCode(),
                ctx.campaign().getLanguage());
        MessageGateway.Result r;
        try {
            r = gateway.send(out);
        } catch (RuntimeException e) {
            r = MessageGateway.Result.fail("Lỗi gateway: " + e.getMessage(), true);
        }
        int attempts = m.attempts() + 1;
        if (r.success()) {
            jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = ?, ATTEMPTS = ?, SENT_AT = ?, DELIVERED_AT = ?, COST = ?, "
                            + "PROVIDER_REF = ?, LAST_ERROR = NULL WHERE ID = ?",
                    r.delivered() ? "DELIVERED" : "SENT", attempts, Utc.ts(now), r.delivered() ? Utc.ts(now) : null,
                    unitPrice, r.providerRef(), m.id());
        } else if (r.retryable() && attempts <= settings.retryMaxAttempts()) {
            Instant retryAt = now.plus(Duration.ofMinutes(settings.retryIntervalMinutes()));
            jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET ATTEMPTS = ?, DUE_AT = ?, LAST_ERROR = ? WHERE ID = ?",
                    attempts, Utc.ts(retryAt), truncate(r.error()), m.id());
        } else {
            boolean refund = settings.refundOnFailure() && m.channel() == Channel.OCS;
            jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = 'FAILED', ATTEMPTS = ?, LAST_ERROR = ?, REFUNDED = ?, "
                    + "COST = 0 WHERE ID = ?", attempts, truncate(r.error()), refund ? 1 : 0, m.id());
        }
    }

    private CampaignCtx load(long campaignId, NotificationSettings settings) {
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
        return new CampaignCtx(c, def, CampaignRunner.silentWindow(def, settings), over);
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
