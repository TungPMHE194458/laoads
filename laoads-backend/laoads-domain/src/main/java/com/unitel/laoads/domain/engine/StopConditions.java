package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.CampaignDefinition.GoalDef;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Điều kiện dừng của chiến dịch (SRS 3.2.6.1 #15): loại khách hàng khỏi danh sách nhận tin của các chu kỳ
 * (lượt chạy mới của chiến dịch lặp lại) và các bước tiếp theo (tin còn chờ gửi) nếu:
 * <ul>
 *   <li>{@code stopOnGoal}: khách hàng đã đạt mục tiêu chiến dịch;</li>
 *   <li>{@code maxNoResponse = N}: N thông báo gần nhất khách hàng nhận được từ chiến dịch đều không được mở / click
 *       và khách hàng chưa chuyển đổi ("không phản hồi sau N lần nhận thông báo liên tiếp").</li>
 * </ul>
 * {@code reentryDays = D}: việc loại chỉ kéo dài D ngày — tính từ lúc chuyển đổi, hoặc từ thông báo cuối cùng
 * không được phản hồi. Không bật thì bị loại vĩnh viễn khỏi chiến dịch.
 */
public final class StopConditions {

    /** ISDN đã chuyển đổi trong chiến dịch. */
    private static final String CONVERTED = "SELECT ISDN FROM LADS_CAMPAIGN_TARGET WHERE CAMPAIGN_ID = ? AND CONVERTED = 1";

    /**
     * ISDN có N thông báo gần nhất (đã gửi, không tính hành động viễn thông) đều không phản hồi. Mỗi tin được đánh
     * dấu phản hồi khi có mở, click hoặc khách hàng của lượt đó đã chuyển đổi.
     */
    private static final String UNRESPONSIVE_INNER = "SELECT m.ISDN, m.SENT_AT, ROW_NUMBER() OVER (PARTITION BY m.ISDN "
            + "ORDER BY m.SENT_AT DESC, m.ID DESC) RN, CASE WHEN m.OPENED_AT IS NULL AND m.CLICKED_AT IS NULL "
            + "AND t.CONVERTED = 0 THEN 0 ELSE 1 END RESP FROM LADS_CAMPAIGN_MESSAGE m "
            + "JOIN LADS_CAMPAIGN_TARGET t ON t.ID = m.TARGET_ID WHERE m.CAMPAIGN_ID = ? "
            + "AND m.STATUS IN ('SENT', 'DELIVERED') AND m.CHANNEL <> 'OCS'";

    private StopConditions() {
    }

    /**
     * Đoạn SQL {@code AND <isdnColumn> NOT IN (...)} để loại khách hàng khi tạo đối tượng cho lượt chạy mới;
     * tham số được thêm vào {@code params} theo đúng thứ tự xuất hiện. Rỗng nếu không có điều kiện dừng.
     */
    public static String exclusionSql(GoalDef goal, long campaignId, String isdnColumn, Instant now, List<Object> params) {
        if (goal == null || !goal.excludesAnyone()) {
            return "";
        }
        Instant cutoff = cutoff(goal, now);
        StringBuilder sql = new StringBuilder();
        if (goal.stopOnGoal()) {
            sql.append(" AND ").append(isdnColumn).append(" NOT IN (").append(CONVERTED);
            params.add(campaignId);
            if (cutoff != null) {
                sql.append(" AND CONVERTED_AT >= ?");
                params.add(Utc.ts(cutoff));
            }
            sql.append(")");
        }
        if (goal.tracksNoResponse()) {
            sql.append(" AND ").append(isdnColumn).append(" NOT IN (").append(unresponsive(goal, campaignId, cutoff, null, params))
                    .append(")");
        }
        return sql.toString();
    }

    /** Trong {@code isdns}, các số đang bị loại theo điều kiện dừng (dùng cho danh sách số thử và tin chờ gửi). */
    public static Set<String> excluded(JdbcTemplate jdbc, GoalDef goal, long campaignId, Collection<String> isdns,
                                       Instant now) {
        if (goal == null || !goal.excludesAnyone() || isdns.isEmpty()) {
            return Collections.emptySet();
        }
        Instant cutoff = cutoff(goal, now);
        Set<String> out = new HashSet<>();
        List<String> all = new ArrayList<>(new HashSet<>(isdns));
        for (int i = 0; i < all.size(); i += 1000) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + 1000));
            String in = String.join(",", Collections.nCopies(chunk.size(), "?"));
            if (goal.stopOnGoal()) {
                List<Object> params = new ArrayList<>(List.of(campaignId));
                StringBuilder sql = new StringBuilder(CONVERTED);
                if (cutoff != null) {
                    sql.append(" AND CONVERTED_AT >= ?");
                    params.add(Utc.ts(cutoff));
                }
                sql.append(" AND ISDN IN (").append(in).append(")");
                params.addAll(chunk);
                out.addAll(jdbc.queryForList(sql.toString(), String.class, params.toArray()));
            }
            if (goal.tracksNoResponse()) {
                List<Object> params = new ArrayList<>();
                String sql = unresponsive(goal, campaignId, cutoff, chunk, params);
                out.addAll(jdbc.queryForList(sql, String.class, params.toArray()));
            }
        }
        return out;
    }

    /** Trong {@code isdns}, các số đang bị loại vì không phản hồi (bước tiếp theo trong lượt bị bỏ qua). */
    public static Set<String> unresponsiveAmong(JdbcTemplate jdbc, GoalDef goal, long campaignId, Collection<String> isdns,
                                                Instant now) {
        if (goal == null || !goal.tracksNoResponse() || isdns.isEmpty()) {
            return Collections.emptySet();
        }
        Instant cutoff = cutoff(goal, now);
        Set<String> out = new HashSet<>();
        List<String> all = new ArrayList<>(new HashSet<>(isdns));
        for (int i = 0; i < all.size(); i += 1000) {
            List<Object> params = new ArrayList<>();
            String sql = unresponsive(goal, campaignId, cutoff, all.subList(i, Math.min(all.size(), i + 1000)), params);
            out.addAll(jdbc.queryForList(sql, String.class, params.toArray()));
        }
        return out;
    }

    private static String unresponsive(GoalDef goal, long campaignId, Instant cutoff, List<String> isdns, List<Object> params) {
        StringBuilder inner = new StringBuilder(UNRESPONSIVE_INNER);
        params.add(campaignId);
        if (isdns != null) {
            inner.append(" AND m.ISDN IN (").append(String.join(",", Collections.nCopies(isdns.size(), "?"))).append(")");
            params.addAll(isdns);
        }
        int n = goal.maxNoResponse();
        StringBuilder sql = new StringBuilder("SELECT ISDN FROM (").append(inner)
                .append(") WHERE RN <= ? GROUP BY ISDN HAVING COUNT(*) >= ? AND MAX(RESP) = 0");
        params.add(n);
        params.add(n);
        if (cutoff != null) {
            sql.append(" AND MAX(SENT_AT) >= ?");
            params.add(Utc.ts(cutoff));
        }
        return sql.toString();
    }

    /** Mốc "quay lại": khách hàng bị loại trước mốc này được nhận tin lại; null = loại vĩnh viễn. */
    static Instant cutoff(GoalDef goal, Instant now) {
        return goal.reentryDays() == null || goal.reentryDays() <= 0 ? null
                : now.minus(Duration.ofDays(goal.reentryDays()));
    }
}
