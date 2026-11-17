package com.unitel.laoads.infra.engine;

import com.unitel.laoads.domain.campaign.CampaignDefinition.GoalDef;
import com.unitel.laoads.domain.engine.ConversionSource;
import com.unitel.laoads.domain.segment.SegmentQueryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đo chuyển đổi theo dữ liệu BI (SRS 3.2.6.1): khách hàng đạt mục tiêu khi bản ghi mới nhất của họ trong
 * LADS_SUBSCRIBER (bảng nạp từ all_report_stl_new) thoả điều kiện của mục tiêu. Điều kiện đi qua
 * {@link SegmentQueryBuilder} — cột lấy từ danh mục, giá trị luôn bind tham số. Mục tiêu không có điều kiện → không
 * ghi nhận chuyển đổi tự động. Bật bằng {@code laoads.engine.conversion=snapshot} (LAOADS_ENGINE_CONVERSION).
 */
@Component
@ConditionalOnProperty(name = "laoads.engine.conversion", havingValue = "snapshot")
@RequiredArgsConstructor
@Slf4j
public class SnapshotConversionSource implements ConversionSource {

    private static final int CHUNK = 1000;

    private final JdbcTemplate jdbc;

    @Override
    public Set<Long> converted(long campaignId, GoalDef goal, Collection<Candidate> candidates) {
        if (goal == null || goal.conditionList().isEmpty() || candidates.isEmpty()) {
            return Collections.emptySet();
        }
        SegmentQueryBuilder.Where where = SegmentQueryBuilder.build(goal.conditionList());
        Map<String, List<Long>> byIsdn = new HashMap<>();
        candidates.forEach(c -> byIsdn.computeIfAbsent(c.isdn(), k -> new ArrayList<>()).add(c.targetId()));
        List<String> isdns = new ArrayList<>(byIsdn.keySet());
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < isdns.size(); i += CHUNK) {
            List<String> chunk = isdns.subList(i, Math.min(isdns.size(), i + CHUNK));
            List<Object> params = new ArrayList<>(chunk);
            params.addAll(where.params());
            String sql = "SELECT ISDN FROM LADS_SUBSCRIBER WHERE ISDN IN ("
                    + String.join(",", Collections.nCopies(chunk.size(), "?")) + ") AND (" + where.sql() + ")";
            for (String isdn : jdbc.queryForList(sql, String.class, params.toArray())) {
                out.addAll(byIsdn.getOrDefault(isdn, List.of()));
            }
        }
        log.debug("[CONVERSION] chiến dịch #{}: {} / {} ứng viên đạt mục tiêu theo dữ liệu BI", campaignId, out.size(),
                candidates.size());
        return out;
    }
}
