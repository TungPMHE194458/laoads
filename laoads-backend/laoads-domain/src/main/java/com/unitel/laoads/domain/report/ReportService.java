package com.unitel.laoads.domain.report;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
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

    /**
     * {@code sent}: tin gửi thành công (SENT + DELIVERED); {@code failed}: tin gửi lỗi (FAILED).
     * Tỷ lệ gửi thành công (SRS 3.2.8.2) = sent / (sent + failed).
     */
    public record ChannelStat(String channel, long sent, long failed, long delivered, long opened, long clicked,
                              long converted) {
    }

    /**
     * Theo nhóm hành động (SRS 3.2.8.7): {@code delivered} = khách hàng đã nhận ít nhất một tin,
     * {@code sentRate} = delivered / target, {@code opens} = khách hàng đã mở, {@code openRate} = opens / delivered,
     * {@code conversions} = khách hàng chuyển đổi, {@code conversionRate} = conversions / target,
     * {@code uplift} = conversionRate − conversionRate nhóm đối chứng (điểm %).
     */
    public record GroupStat(String groupName, boolean isControl, long target, long delivered, double sentRate,
                            long failed, long opens, double openRate, long clicks, double clickRate, long conversions,
                            double conversionRate, Double uplift) {
    }

    /**
     * Theo từng hành động (bảng "Hành động" của laoads.com): đếm theo tin — {@code sent} gửi thành công, {@code failed}
     * gửi lỗi, {@code delivered}, {@code opens}, {@code clicks}; {@code conversions} = khách hàng nhận hành động này đã
     * chuyển đổi. FE tự tính tỷ lệ (gửi = sent / (sent + failed), mở / click = / delivered, chuyển đổi = / sent).
     */
    public record ActionStat(String groupKey, String groupName, String actionKey, int actionIndex, String actionType,
                             String channel, long sent, long failed, long delivered, long opens, long clicks,
                             long conversions) {
    }

    public record CampaignReport(Totals totals, List<TimePoint> timeseries, List<ChannelStat> channels, List<GroupStat> groups,
                                 List<ActionStat> actions) {
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

    /** Kênh gửi tin có báo cáo (OCS = hành động viễn thông, không phải tin nhắn). */
    public static final List<String> MESSAGE_CHANNELS = List.of("SMS", "EMAIL", "WHATSAPP", "USSD");
    /** Số mốc tối đa được điền 0 cho khoảng ngày (tránh sinh quá nhiều điểm khi chọn khoảng rất dài). */
    private static final int MAX_FILLED_POINTS = 400;

    @Transactional(readOnly = true)
    public CampaignReport campaignReport(long ws, long campaignId, String bucket) {
        return campaignReport(ws, campaignId, bucket, null, null, null);
    }

    /**
     * SRS 3.2.8. {@code from}/{@code to}: khoảng ngày (giờ Lào, gồm cả hai đầu) chỉ áp cho chuỗi thời gian (3.2.8.1);
     * {@code channel}: lọc chuỗi thời gian theo một kênh. Mỗi chỉ số được tính theo thời điểm phát sinh của nó
     * (gửi theo SENT_AT, mở theo OPENED_AT, click theo CLICKED_AT, chuyển đổi theo CONVERTED_AT).
     */
    @Transactional(readOnly = true)
    public CampaignReport campaignReport(long ws, long campaignId, String bucket, LocalDate from, LocalDate to,
                                         String channel) {
        Campaign c = campaignService.get(ws, campaignId);
        String unit = bucket == null ? "DAY" : bucket;
        String fmt = switch (unit) {
            case "WEEK" -> "IW";
            case "MONTH" -> "MM";
            default -> "DD";
        };
        String ch = channel == null || channel.isBlank() ? null : channel.trim().toUpperCase(java.util.Locale.ROOT);
        if (ch != null && !MESSAGE_CHANNELS.contains(ch)) {
            throw LaoadsException.validation(List.of(new FieldViolation("channel", "error.validation.invalidValue",
                    "Giá trị không hợp lệ")));
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw LaoadsException.validation(List.of(new FieldViolation("to", "error.validation.endBeforeStart",
                    "Thời gian kết thúc phải sau thời gian bắt đầu")));
        }
        Instant fromTs = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant toTs = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();

        Long runTargets = jdbc.queryForObject("SELECT SUM(TARGET_COUNT) FROM LADS_CAMPAIGN_RUN WHERE CAMPAIGN_ID = ?",
                Long.class, campaignId);
        long target = runTargets != null && runTargets > 0 ? runTargets : c.getTargetCount();
        Totals totals = new Totals(target, c.getSentCount(), c.getDeliveredCount(), c.getFailedCount(), c.getOpenCount(),
                c.getClickCount(), c.getConversionCount(), c.getSpent());

        Map<String, long[]> series = new java.util.TreeMap<>();
        if (from != null && to != null) {
            fillBuckets(series, from, to, unit);
        }
        countMessages(series, 0, "SENT_AT", "AND m.STATUS IN ('SENT', 'DELIVERED') ", fmt, campaignId, ch, fromTs, toTs);
        countMessages(series, 1, "OPENED_AT", "", fmt, campaignId, ch, fromTs, toTs);
        countMessages(series, 2, "CLICKED_AT", "", fmt, campaignId, ch, fromTs, toTs);
        String convDay = String.format(LOCAL_DAY, "t.CONVERTED_AT", fmt);
        List<Object> params = new ArrayList<>(List.of(campaignId));
        StringBuilder sql = new StringBuilder("SELECT TO_CHAR(" + convDay + ", 'YYYY-MM-DD'), COUNT(*) FROM LADS_CAMPAIGN_TARGET t "
                + "WHERE t.CAMPAIGN_ID = ? AND t.CONVERTED = 1 AND t.IS_CONTROL = 0 AND t.CONVERTED_AT IS NOT NULL ");
        if (ch != null) {
            sql.append("AND EXISTS (SELECT 1 FROM LADS_CAMPAIGN_MESSAGE m WHERE m.TARGET_ID = t.ID AND m.CHANNEL = ?) ");
            params.add(ch);
        }
        appendRange(sql, params, "t.CONVERTED_AT", fromTs, toTs);
        sql.append("GROUP BY ").append(convDay);
        jdbc.query(sql.toString(), rs -> {
            series.computeIfAbsent(rs.getString(1), k -> new long[4])[3] = rs.getLong(2);
        }, params.toArray());
        List<TimePoint> timeseries = series.entrySet().stream()
                .map(e -> new TimePoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();

        List<ChannelStat> channels = channelStats("m.CAMPAIGN_ID = ?", campaignId);
        return new CampaignReport(totals, timeseries, channels, groupStats(c), actionStats(c));
    }

    private void countMessages(Map<String, long[]> series, int idx, String column, String extra, String fmt,
                               long campaignId, String channel, Instant fromTs, Instant toTs) {
        String day = String.format(LOCAL_DAY, "m." + column, fmt);
        List<Object> params = new ArrayList<>(List.of(campaignId));
        StringBuilder sql = new StringBuilder("SELECT TO_CHAR(" + day + ", 'YYYY-MM-DD'), COUNT(*) FROM LADS_CAMPAIGN_MESSAGE m "
                + "WHERE m.CAMPAIGN_ID = ? AND m.CHANNEL <> 'OCS' AND m." + column + " IS NOT NULL " + extra);
        if (channel != null) {
            sql.append("AND m.CHANNEL = ? ");
            params.add(channel);
        }
        appendRange(sql, params, "m." + column, fromTs, toTs);
        sql.append("GROUP BY ").append(day);
        jdbc.query(sql.toString(), rs -> {
            series.computeIfAbsent(rs.getString(1), k -> new long[4])[idx] = rs.getLong(2);
        }, params.toArray());
    }

    private static void appendRange(StringBuilder sql, List<Object> params, String column, Instant fromTs, Instant toTs) {
        if (fromTs != null) {
            sql.append("AND ").append(column).append(" >= ? ");
            params.add(Utc.ts(fromTs));
        }
        if (toTs != null) {
            sql.append("AND ").append(column).append(" < ? ");
            params.add(Utc.ts(toTs));
        }
    }

    /** Điền mốc 0 cho cả khoảng (đầu tuần ISO = Thứ 2 như TRUNC(..., 'IW'); đầu tháng như TRUNC(..., 'MM')). */
    static void fillBuckets(Map<String, long[]> series, LocalDate from, LocalDate to, String unit) {
        LocalDate x = switch (unit) {
            case "WEEK" -> from.with(java.time.DayOfWeek.MONDAY);
            case "MONTH" -> from.withDayOfMonth(1);
            default -> from;
        };
        for (int n = 0; !x.isAfter(to) && n < MAX_FILLED_POINTS; n++) {
            series.put(x.toString(), new long[4]);
            x = switch (unit) {
                case "WEEK" -> x.plusWeeks(1);
                case "MONTH" -> x.plusMonths(1);
                default -> x.plusDays(1);
            };
        }
    }

    private List<GroupStat> groupStats(Campaign c) {
        CampaignDefinition def = campaignService.definitionOf(c);
        Map<String, String> names = new HashMap<>();
        List<ActionGroupDef> groups = def.groups();
        for (int i = 0; i < groups.size(); i++) {
            ActionGroupDef g = groups.get(i);
            names.put(g.id() == null || g.id().isBlank() ? "g" + i : g.id(), g.name());
        }
        record Row(String key, boolean control, long target, long reached, long opened, long converted, long failed,
                   long clicked) {
        }
        // COUNT(DISTINCT t.ID): mỗi đối tượng có thể có nhiều tin (chuỗi hành động) sau LEFT JOIN
        List<Row> rows = jdbc.query("SELECT t.GROUP_KEY, MAX(t.IS_CONTROL), COUNT(DISTINCT t.ID), "
                + "COUNT(DISTINCT CASE WHEN m.STATUS IN ('SENT', 'DELIVERED') THEN t.ID END), "
                + "COUNT(DISTINCT CASE WHEN m.OPENED_AT IS NOT NULL THEN t.ID END), "
                + "COUNT(DISTINCT CASE WHEN t.CONVERTED = 1 THEN t.ID END), "
                + "COUNT(DISTINCT CASE WHEN m.STATUS = 'FAILED' THEN t.ID END), "
                + "COUNT(DISTINCT CASE WHEN m.CLICKED_AT IS NOT NULL THEN t.ID END) "
                + "FROM LADS_CAMPAIGN_TARGET t LEFT JOIN LADS_CAMPAIGN_MESSAGE m ON m.TARGET_ID = t.ID "
                + "WHERE t.CAMPAIGN_ID = ? GROUP BY t.GROUP_KEY",
                (rs, i) -> new Row(rs.getString(1), rs.getInt(2) == 1, rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6), rs.getLong(7), rs.getLong(8)), c.getId());
        double controlRate = rows.stream().filter(Row::control).mapToDouble(r -> ratio(r.converted(), r.target()))
                .findFirst().orElse(Double.NaN);
        List<GroupStat> out = new ArrayList<>();
        // giữ thứ tự nhóm như cấu hình; nhóm chưa có đối tượng hiển thị số 0
        for (int i = 0; i < groups.size(); i++) {
            ActionGroupDef g = groups.get(i);
            String key = g.id() == null || g.id().isBlank() ? "g" + i : g.id();
            Row r = rows.stream().filter(x -> x.key().equals(key)).findFirst()
                    .orElse(new Row(key, g.isControl(), 0, 0, 0, 0, 0, 0));
            double conv = ratio(r.converted(), r.target());
            Double uplift = g.isControl() || Double.isNaN(controlRate) || r.target() == 0 ? null
                    : Math.round((conv - controlRate) * 1000) / 10.0;
            out.add(new GroupStat(names.getOrDefault(key, key), g.isControl(), r.target(), r.reached(),
                    ratio(r.reached(), r.target()), r.failed(), r.opened(), ratio(r.opened(), r.reached()), r.clicked(),
                    ratio(r.clicked(), r.reached()), r.converted(), conv, uplift));
        }
        return out;
    }

    /** Thống kê theo từng hành động, giữ thứ tự nhóm / hành động như cấu hình. */
    private List<ActionStat> actionStats(Campaign c) {
        CampaignDefinition def = campaignService.definitionOf(c);
        Map<String, long[]> counts = new HashMap<>();
        jdbc.query("SELECT m.GROUP_KEY, m.ACTION_KEY, SUM(CASE WHEN m.STATUS IN ('SENT', 'DELIVERED') THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN m.STATUS = 'FAILED' THEN 1 ELSE 0 END), SUM(CASE WHEN m.STATUS = 'DELIVERED' THEN 1 ELSE 0 END), "
                + "COUNT(m.OPENED_AT), COUNT(m.CLICKED_AT), "
                + "COUNT(DISTINCT CASE WHEN t.CONVERTED = 1 AND m.STATUS IN ('SENT', 'DELIVERED') THEN t.ID END) "
                + "FROM LADS_CAMPAIGN_MESSAGE m JOIN LADS_CAMPAIGN_TARGET t ON t.ID = m.TARGET_ID "
                + "WHERE m.CAMPAIGN_ID = ? GROUP BY m.GROUP_KEY, m.ACTION_KEY", rs -> {
            counts.put(rs.getString(1) + "|" + rs.getString(2), new long[]{rs.getLong(3), rs.getLong(4), rs.getLong(5),
                    rs.getLong(6), rs.getLong(7), rs.getLong(8)});
        }, c.getId());
        List<ActionStat> out = new ArrayList<>();
        List<ActionGroupDef> groups = def.groups();
        for (int gi = 0; gi < groups.size(); gi++) {
            ActionGroupDef g = groups.get(gi);
            if (g.isControl() || g.actions() == null) {
                continue;
            }
            String gk = g.id() == null || g.id().isBlank() ? "g" + gi : g.id();
            for (int ai = 0; ai < g.actions().size(); ai++) {
                var a = g.actions().get(ai);
                if (a.type() == null) {
                    continue;
                }
                String ak = a.id() == null || a.id().isBlank() ? "a" + ai : a.id();
                long[] v = counts.getOrDefault(gk + "|" + ak, new long[6]);
                out.add(new ActionStat(gk, g.name(), ak, ai + 1, a.type().name(), a.channel().name(), v[0], v[1], v[2],
                        v[3], v[4], v[5]));
            }
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
        for (String ch : MESSAGE_CHANNELS) {
            byChannel.put(ch, new ChannelStat(ch, 0, 0, 0, 0, 0, 0));
        }
        jdbc.query("SELECT m.CHANNEL, SUM(CASE WHEN m.STATUS IN ('SENT', 'DELIVERED') THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN m.STATUS = 'FAILED' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN m.STATUS = 'DELIVERED' THEN 1 ELSE 0 END), COUNT(m.OPENED_AT), COUNT(m.CLICKED_AT), "
                + "COUNT(DISTINCT CASE WHEN t.CONVERTED = 1 THEN t.ID END) FROM LADS_CAMPAIGN_MESSAGE m "
                + "JOIN LADS_CAMPAIGN_TARGET t ON t.ID = m.TARGET_ID WHERE m.CHANNEL <> 'OCS' AND " + where
                + " GROUP BY m.CHANNEL", rs -> {
            byChannel.put(rs.getString(1), new ChannelStat(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                    rs.getLong(5), rs.getLong(6), rs.getLong(7)));
        }, params);
        // kênh lỗi 100% vẫn hiện (tỷ lệ gửi thành công 0%) — SRS 3.2.8.2
        return byChannel.values().stream().filter(s -> s.sent() + s.failed() > 0).toList();
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
        // SRS 3.1.2: số chiến dịch đang chạy tại cùng thời điểm kỳ trước — có lượt chạy đã bắt đầu và chưa kết thúc
        long runningBefore = count("SELECT COUNT(DISTINCT CAMPAIGN_ID) FROM LADS_CAMPAIGN_RUN WHERE STARTED_AT <= ? "
                + "AND (FINISHED_AT IS NULL OR FINISHED_AT > ?)", before, before);
        return new AdminDashboard(Metric.of(activeWs, activeWsBefore), Metric.of(users, usersBefore),
                Metric.of(campaignRepo.countRunning(), runningBefore), Metric.of(campaigns, campaignsBefore),
                topWorkspaces(TOP_WORKSPACES), days);
    }

    /** Số workspace trong bảng Top (SRS 3.1.6) — file Excel xuất (3.1.7) dùng cùng số này để khớp màn hình. */
    public static final int TOP_WORKSPACES = 10;

    /** SRS 3.1.6: chỉ workspace có ít nhất một chiến dịch, giảm dần theo số chiến dịch. */
    @Transactional(readOnly = true)
    public List<TopWorkspace> topWorkspaces(int limit) {
        return jdbc.query("SELECT * FROM (SELECT w.ID, w.CODE, w.NAME, COUNT(c.ID) TOTAL, "
                        + "NVL(SUM(CASE WHEN c.STATUS = 'RUNNING' THEN 1 ELSE 0 END), 0) RUNNING FROM LADS_WORKSPACE w "
                        + "JOIN LADS_CAMPAIGN c ON c.WORKSPACE_ID = w.ID GROUP BY w.ID, w.CODE, w.NAME "
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
