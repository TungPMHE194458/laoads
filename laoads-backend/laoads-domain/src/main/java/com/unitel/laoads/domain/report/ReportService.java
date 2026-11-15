package com.unitel.laoads.domain.report;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.engine.Utc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Báo cáo chiến dịch (SRS 3.2.7–3.2.8), Dashboard workspace và Dashboard Super Admin (SRS 3.1). */
@Service
@RequiredArgsConstructor
public class ReportService {

    public record Metric(long total, Long previousTotal, Double growth) {
        static Metric of(long total, Long prev) {
            Double g = prev == null || prev == 0 ? null : (total - prev) * 100.0 / prev;
            return new Metric(total, prev, g);
        }
    }

    public record Totals(long target, long sent, long delivered, long failed, long opens, long clicks, long conversions,
                         BigDecimal spent) {
    }

    public record TimePoint(String date, long sent, long opens, long clicks, long conversions) {
    }

    public record ChannelStat(String channel, long sent, long delivered, long opened, long clicked, long converted) {
    }

    public record GroupStat(String groupName, boolean isControl, long target, double sentRate, double openRate,
                            double conversionRate, Double uplift) {
    }

    public record CampaignReport(Totals totals, List<TimePoint> timeseries, List<ChannelStat> channels, List<GroupStat> groups) {
    }

    public record DashboardSummary(Metric runningCampaigns, Metric pendingApprovals, Metric totalCampaigns,
                                   Metric reachedCustomers, int periodDays) {
    }

    public record TopWorkspace(long workspaceId, String code, String name, long campaignCount, long runningCount) {
    }

    public record AdminDashboard(Metric activeWorkspaces, Metric totalUsers, Metric runningCampaigns,
                                 Metric totalCampaigns, List<TopWorkspace> topWorkspaces, int periodDays) {
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Vientiane");
    /** Ngày theo giờ Lào (UTC+7, không có giờ mùa hè) từ cột TIMESTAMP lưu UTC. */
    private static final String LOCAL_DAY = "TRUNC(CAST(%s + INTERVAL '7' HOUR AS DATE), '%s')";

    private final JdbcTemplate jdbc;
    private final CampaignRepository campaignRepo;
    private final CampaignService campaignService;

    // ───────────── Báo cáo chiến dịch ─────────────

    @Transactional(readOnly = true)
    public CampaignReport campaignReport(long ws, long campaignId, String bucket) {
        Campaign c = campaignService.get(ws, campaignId);
        String fmt = switch (bucket == null ? "DAY" : bucket) {
            case "WEEK" -> "IW";
            case "MONTH" -> "MM";
            default -> "DD";
        };
        Long runTargets = jdbc.queryForObject("SELECT SUM(TARGET_COUNT) FROM LADS_CAMPAIGN_RUN WHERE CAMPAIGN_ID = ?",
                Long.class, campaignId);
        long target = runTargets != null && runTargets > 0 ? runTargets : c.getTargetCount();
        Totals totals = new Totals(target, c.getSentCount(), c.getDeliveredCount(), c.getFailedCount(), c.getOpenCount(),
                c.getClickCount(), c.getConversionCount(), c.getSpent());

        Map<String, long[]> series = new LinkedHashMap<>();
        String day = String.format(LOCAL_DAY, "SENT_AT", fmt);
        jdbc.query("SELECT TO_CHAR(" + day + ", 'YYYY-MM-DD') D, COUNT(*), COUNT(OPENED_AT), COUNT(CLICKED_AT) "
                + "FROM LADS_CAMPAIGN_MESSAGE WHERE CAMPAIGN_ID = ? AND SENT_AT IS NOT NULL GROUP BY " + day
                + " ORDER BY " + day, rs -> {
            series.put(rs.getString(1), new long[]{rs.getLong(2), rs.getLong(3), rs.getLong(4), 0});
        }, campaignId);
        String convDay = String.format(LOCAL_DAY, "CONVERTED_AT", fmt);
        jdbc.query("SELECT TO_CHAR(" + convDay + ", 'YYYY-MM-DD'), COUNT(*) FROM LADS_CAMPAIGN_TARGET WHERE CAMPAIGN_ID = ? "
                + "AND CONVERTED = 1 AND IS_CONTROL = 0 GROUP BY " + convDay, rs -> {
            series.computeIfAbsent(rs.getString(1), k -> new long[4])[3] = rs.getLong(2);
        }, campaignId);
        List<TimePoint> timeseries = series.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new TimePoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();

        List<ChannelStat> channels = channelStats("m.CAMPAIGN_ID = ?", campaignId);
        return new CampaignReport(totals, timeseries, channels, groupStats(c));
    }

    private List<GroupStat> groupStats(Campaign c) {
        CampaignDefinition def = campaignService.definitionOf(c);
        Map<String, String> names = new HashMap<>();
        List<ActionGroupDef> groups = def.groups();
        for (int i = 0; i < groups.size(); i++) {
            ActionGroupDef g = groups.get(i);
            names.put(g.id() == null || g.id().isBlank() ? "g" + i : g.id(), g.name());
        }
        record Row(String key, boolean control, long target, long reached, long opened, long converted) {
        }
        // COUNT(DISTINCT t.ID): mỗi đối tượng có thể có nhiều tin (chuỗi hành động) sau LEFT JOIN
        List<Row> rows = jdbc.query("SELECT t.GROUP_KEY, MAX(t.IS_CONTROL), COUNT(DISTINCT t.ID), "
                + "COUNT(DISTINCT CASE WHEN m.STATUS IN ('SENT', 'DELIVERED') THEN t.ID END), "
                + "COUNT(DISTINCT CASE WHEN m.OPENED_AT IS NOT NULL THEN t.ID END), "
                + "COUNT(DISTINCT CASE WHEN t.CONVERTED = 1 THEN t.ID END) "
                + "FROM LADS_CAMPAIGN_TARGET t LEFT JOIN LADS_CAMPAIGN_MESSAGE m ON m.TARGET_ID = t.ID "
                + "WHERE t.CAMPAIGN_ID = ? GROUP BY t.GROUP_KEY",
                (rs, i) -> new Row(rs.getString(1), rs.getInt(2) == 1, rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6)), c.getId());
        double controlRate = rows.stream().filter(Row::control).mapToDouble(r -> ratio(r.converted(), r.target()))
                .findFirst().orElse(Double.NaN);
        List<GroupStat> out = new ArrayList<>();
        // giữ thứ tự nhóm như cấu hình; nhóm chưa có đối tượng hiển thị số 0
        for (int i = 0; i < groups.size(); i++) {
            ActionGroupDef g = groups.get(i);
            String key = g.id() == null || g.id().isBlank() ? "g" + i : g.id();
            Row r = rows.stream().filter(x -> x.key().equals(key)).findFirst()
                    .orElse(new Row(key, g.isControl(), 0, 0, 0, 0));
            double conv = ratio(r.converted(), r.target());
            Double uplift = g.isControl() || Double.isNaN(controlRate) || r.target() == 0 ? null
                    : Math.round((conv - controlRate) * 1000) / 10.0;
            out.add(new GroupStat(names.getOrDefault(key, key), g.isControl(), r.target(), ratio(r.reached(), r.target()),
                    ratio(r.opened(), r.reached()), conv, uplift));
        }
        return out;
    }

    // ───────────── Dashboard workspace ─────────────

    @Transactional(readOnly = true)
    public DashboardSummary summary(long ws, int periodDays) {
        int days = clampDays(periodDays);
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(days));
        Instant prevFrom = from.minus(Duration.ofDays(days));
        long running = campaignRepo.countByWorkspaceIdAndStatus(ws, Status.RUNNING);
        long pending = campaignRepo.countByWorkspaceIdAndStatus(ws, Status.PENDING_APPROVAL);
        long total = campaignRepo.countByWorkspaceId(ws);
        long totalBefore = campaignRepo.countCreatedBefore(ws, from);
        long reached = reached(ws, from, now);
        long reachedPrev = reached(ws, prevFrom, from);
        return new DashboardSummary(Metric.of(running, null), Metric.of(pending, null), Metric.of(total, totalBefore),
                Metric.of(reached, reachedPrev), days);
    }

    private long reached(long ws, Instant from, Instant to) {
        Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT m.ISDN) FROM LADS_CAMPAIGN_MESSAGE m JOIN LADS_CAMPAIGN c "
                + "ON c.ID = m.CAMPAIGN_ID WHERE c.WORKSPACE_ID = ? AND m.STATUS IN ('SENT', 'DELIVERED') "
                + "AND m.SENT_AT >= ? AND m.SENT_AT < ?", Long.class, ws, Utc.ts(from), Utc.ts(to));
        return n == null ? 0 : n;
    }

    @Transactional(readOnly = true)
    public List<TimePoint> trend(long ws, int days) {
        int d = clampDays(days);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(d - 1L);
        Instant from = first.atStartOfDay(ZONE).toInstant();
        Map<String, long[]> series = new LinkedHashMap<>();
        for (LocalDate x = first; !x.isAfter(today); x = x.plusDays(1)) {
            series.put(x.toString(), new long[4]);
        }
        String day = String.format(LOCAL_DAY, "m.SENT_AT", "DD");
        jdbc.query("SELECT TO_CHAR(" + day + ", 'YYYY-MM-DD'), COUNT(*), COUNT(m.OPENED_AT), COUNT(m.CLICKED_AT) "
                + "FROM LADS_CAMPAIGN_MESSAGE m JOIN LADS_CAMPAIGN c ON c.ID = m.CAMPAIGN_ID WHERE c.WORKSPACE_ID = ? "
                + "AND m.SENT_AT >= ? GROUP BY " + day, rs -> {
            long[] v = series.get(rs.getString(1));
            if (v != null) {
                v[0] = rs.getLong(2);
                v[1] = rs.getLong(3);
                v[2] = rs.getLong(4);
            }
        }, ws, Utc.ts(from));
        String convDay = String.format(LOCAL_DAY, "t.CONVERTED_AT", "DD");
        jdbc.query("SELECT TO_CHAR(" + convDay + ", 'YYYY-MM-DD'), COUNT(*) FROM LADS_CAMPAIGN_TARGET t JOIN LADS_CAMPAIGN c "
                + "ON c.ID = t.CAMPAIGN_ID WHERE c.WORKSPACE_ID = ? AND t.CONVERTED = 1 AND t.IS_CONTROL = 0 "
                + "AND t.CONVERTED_AT >= ? GROUP BY " + convDay, rs -> {
            long[] v = series.get(rs.getString(1));
            if (v != null) {
                v[3] = rs.getLong(2);
            }
        }, ws, Utc.ts(from));
        return series.entrySet().stream()
                .map(e -> new TimePoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChannelStat> channels(long ws, int days) {
        Instant from = Instant.now().minus(Duration.ofDays(clampDays(days)));
        return channelStats("m.CAMPAIGN_ID IN (SELECT ID FROM LADS_CAMPAIGN WHERE WORKSPACE_ID = ?) AND m.SENT_AT >= ?",
                ws, Utc.ts(from));
    }

    private List<ChannelStat> channelStats(String where, Object... params) {
        Map<String, ChannelStat> byChannel = new LinkedHashMap<>();
        for (String ch : List.of("SMS", "EMAIL", "WHATSAPP", "USSD")) {
            byChannel.put(ch, new ChannelStat(ch, 0, 0, 0, 0, 0));
        }
        jdbc.query("SELECT m.CHANNEL, SUM(CASE WHEN m.STATUS IN ('SENT', 'DELIVERED') THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN m.STATUS = 'DELIVERED' THEN 1 ELSE 0 END), COUNT(m.OPENED_AT), COUNT(m.CLICKED_AT), "
                + "COUNT(DISTINCT CASE WHEN t.CONVERTED = 1 THEN t.ID END) FROM LADS_CAMPAIGN_MESSAGE m "
                + "JOIN LADS_CAMPAIGN_TARGET t ON t.ID = m.TARGET_ID WHERE m.CHANNEL <> 'OCS' AND " + where
                + " GROUP BY m.CHANNEL", rs -> {
            byChannel.put(rs.getString(1), new ChannelStat(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                    rs.getLong(5), rs.getLong(6)));
        }, params);
        return byChannel.values().stream().filter(s -> s.sent() > 0).toList();
    }

    // ───────────── Dashboard Super Admin ─────────────

    @Transactional(readOnly = true)
    public AdminDashboard adminDashboard(int periodDays) {
        int days = clampDays(periodDays);
        Instant before = Instant.now().minus(Duration.ofDays(days));
        long activeWs = count("SELECT COUNT(*) FROM LADS_WORKSPACE WHERE STATUS = 'ACTIVE'");
        long activeWsBefore = count("SELECT COUNT(*) FROM LADS_WORKSPACE WHERE STATUS = 'ACTIVE' AND CREATED_AT < ?", before);
        long users = count("SELECT COUNT(*) FROM LADS_USER");
        long usersBefore = count("SELECT COUNT(*) FROM LADS_USER WHERE CREATED_AT < ?", before);
        long campaigns = count("SELECT COUNT(*) FROM LADS_CAMPAIGN");
        long campaignsBefore = count("SELECT COUNT(*) FROM LADS_CAMPAIGN WHERE CREATED_AT < ?", before);
        return new AdminDashboard(Metric.of(activeWs, activeWsBefore), Metric.of(users, usersBefore),
                Metric.of(campaignRepo.countRunning(), null), Metric.of(campaigns, campaignsBefore), topWorkspaces(10), days);
    }

    @Transactional(readOnly = true)
    public List<TopWorkspace> topWorkspaces(int limit) {
        return jdbc.query("SELECT * FROM (SELECT w.ID, w.CODE, w.NAME, COUNT(c.ID) TOTAL, "
                        + "NVL(SUM(CASE WHEN c.STATUS = 'RUNNING' THEN 1 ELSE 0 END), 0) RUNNING FROM LADS_WORKSPACE w "
                        + "LEFT JOIN LADS_CAMPAIGN c ON c.WORKSPACE_ID = w.ID GROUP BY w.ID, w.CODE, w.NAME "
                        + "ORDER BY COUNT(c.ID) DESC, w.CODE) WHERE ROWNUM <= ?",
                (rs, i) -> new TopWorkspace(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)),
                limit);
    }

    private long count(String sql, Instant... at) {
        Object[] params = java.util.Arrays.stream(at).map(Utc::ts).toArray();
        Long n = jdbc.queryForObject(sql, Long.class, params);
        return n == null ? 0 : n;
    }

    private static int clampDays(int d) {
        return d < 1 ? 7 : Math.min(d, 365);
    }

    private static double ratio(long a, long b) {
        return b <= 0 ? 0 : (double) a / b;
    }
}
