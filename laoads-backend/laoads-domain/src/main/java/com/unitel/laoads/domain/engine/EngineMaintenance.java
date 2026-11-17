package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignEnums.Type;
import com.unitel.laoads.domain.campaign.CampaignHistory;
import com.unitel.laoads.domain.campaign.CampaignHistoryRepository;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.segment.SegmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Engine — việc định kỳ: đóng lượt, hoàn tất chiến dịch, tổng hợp số liệu, đo chuyển đổi. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EngineMaintenance {

    /** Ứng viên chuyển đổi phải nhận tin ít nhất chừng này trước khi được đo. */
    static final Duration CONVERSION_MIN_AGE = Duration.ofMinutes(2);
    static final int CONVERSION_BATCH = 5000;

    private final JdbcTemplate jdbc;
    private final CampaignRepository campaignRepo;
    private final CampaignHistoryRepository historyRepo;
    private final CampaignService campaignService;
    private final SegmentService segmentService;
    private final ConversionSource conversionSource;

    /** Lượt (chiến dịch STATIC) không còn tin PENDING → COMPLETED. */
    @Transactional
    public int finalizeRuns() {
        return jdbc.update("UPDATE LADS_CAMPAIGN_RUN r SET STATUS = 'COMPLETED', FINISHED_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP) "
                + "WHERE r.STATUS = 'RUNNING' AND r.CAMPAIGN_ID IN (SELECT c.ID FROM LADS_CAMPAIGN c WHERE c.CAMPAIGN_TYPE = 'STATIC') "
                + "AND NOT EXISTS (SELECT 1 FROM LADS_CAMPAIGN_MESSAGE m WHERE m.RUN_ID = r.ID AND m.STATUS = 'PENDING')");
    }

    /** Chiến dịch hết lượt (hoặc quá endAt) và không còn lượt đang chạy → COMPLETED. */
    @Transactional(readOnly = true)
    public List<Long> completable() {
        return jdbc.queryForList("SELECT c.ID FROM LADS_CAMPAIGN c WHERE c.STATUS = 'RUNNING' AND c.CAMPAIGN_TYPE = 'STATIC' "
                + "AND (c.NEXT_RUN_AT IS NULL OR (c.END_AT IS NOT NULL AND c.END_AT < SYS_EXTRACT_UTC(SYSTIMESTAMP))) "
                + "AND c.RUN_COUNT > 0 AND NOT EXISTS (SELECT 1 FROM LADS_CAMPAIGN_RUN r WHERE r.CAMPAIGN_ID = c.ID "
                + "AND r.STATUS = 'RUNNING')", Long.class);
    }

    @Transactional
    public void complete(long campaignId) {
        Campaign c = campaignRepo.findById(campaignId).orElse(null);
        if (c == null || !c.is(Status.RUNNING) || c.getType() != Type.STATIC) {
            return;
        }
        refresh(c);
        c.setStatus(Status.COMPLETED);
        c.setNextRunAt(null);
        c.setCompletedAt(Instant.now());
        historyRepo.save(CampaignHistory.of(campaignId, "system", "COMPLETE",
                "Đã gửi " + c.getSentCount() + " tin, " + c.getConversionCount() + " chuyển đổi"));
        CampaignDefinition def = campaignService.definitionOf(c);
        // "Trích xuất khách hàng" (laoads.com): mỗi nhóm đã chọn thành một tệp CAMPAIGN_OUTCOME
        List<String> outcomes = def.goal() == null ? List.of() : def.goal().outcomeList();
        boolean hasGoal = def.goal() != null && def.goal().hasGoal();
        if (c.getSentCount() > 0) {
            for (String o : outcomes) {
                if (!hasGoal && !"WHATSAPP_FAILED".equals(o)) {
                    continue; // không có mục tiêu thì không phân được đạt / chưa đạt
                }
                var seg = segmentService.createCampaignOutcome(c.getWorkspaceId(), campaignId, c.getName(), o);
                log.info("[ENGINE] Chiến dịch #{} hoàn tất — tạo tệp {} #{} ({} thuê bao)", campaignId, o, seg.getId(),
                        seg.getTotalUsers());
            }
        }
        log.info("[ENGINE] Chiến dịch #{} hoàn tất", campaignId);
    }

    /** Cập nhật bộ đếm cho chiến dịch đang hoạt động / mới hoàn tất. */
    @Transactional
    public int refreshStats() {
        Instant recent = Instant.now().minus(Duration.ofDays(1));
        List<Campaign> list = campaignRepo.findByStatusIn(EnumSet.of(Status.RUNNING, Status.PAUSED, Status.COMPLETED,
                Status.CANCELLED));
        int n = 0;
        for (Campaign c : list) {
            boolean active = c.is(Status.RUNNING) || c.is(Status.PAUSED);
            boolean fresh = c.getUpdatedAt() != null && c.getUpdatedAt().isAfter(recent)
                    || c.getCompletedAt() != null && c.getCompletedAt().isAfter(recent);
            if (active || fresh) {
                refresh(c);
                n++;
            }
        }
        return n;
    }

    void refresh(Campaign c) {
        long id = c.getId();
        jdbc.query("SELECT NVL(SUM(CASE WHEN STATUS IN ('SENT', 'DELIVERED') THEN 1 ELSE 0 END), 0) SENT, "
                + "NVL(SUM(CASE WHEN STATUS = 'DELIVERED' THEN 1 ELSE 0 END), 0) DELIVERED, "
                + "NVL(SUM(CASE WHEN STATUS = 'FAILED' THEN 1 ELSE 0 END), 0) FAILED, "
                + "COUNT(OPENED_AT) OPENS, COUNT(CLICKED_AT) CLICKS, NVL(SUM(COST), 0) SPENT, "
                + "COUNT(DISTINCT CASE WHEN STATUS IN ('SENT', 'DELIVERED') THEN TARGET_ID END) REACHED "
                + "FROM LADS_CAMPAIGN_MESSAGE WHERE CAMPAIGN_ID = ?", rs -> {
            c.setSentCount(rs.getInt("SENT"));
            c.setDeliveredCount(rs.getInt("DELIVERED"));
            c.setFailedCount(rs.getInt("FAILED"));
            c.setOpenCount(rs.getInt("OPENS"));
            c.setClickCount(rs.getInt("CLICKS"));
            c.setSpent(rs.getBigDecimal("SPENT"));
            c.setReachedCount(rs.getInt("REACHED"));
        }, id);
        long[] treat = {0, 0};
        long[] ctrl = {0, 0};
        jdbc.query("SELECT IS_CONTROL, COUNT(*), NVL(SUM(CONVERTED), 0) FROM LADS_CAMPAIGN_TARGET WHERE CAMPAIGN_ID = ? "
                + "GROUP BY IS_CONTROL", rs -> {
            long[] t = rs.getInt(1) == 1 ? ctrl : treat;
            t[0] = rs.getLong(2);
            t[1] = rs.getLong(3);
        }, id);
        c.setConversionCount((int) treat[1]);
        if (ctrl[0] > 0 && treat[0] > 0) {
            BigDecimal treatRate = BigDecimal.valueOf(treat[1]).divide(BigDecimal.valueOf(treat[0]), 6, RoundingMode.HALF_UP);
            BigDecimal ctrlRate = BigDecimal.valueOf(ctrl[1]).divide(BigDecimal.valueOf(ctrl[0]), 6, RoundingMode.HALF_UP);
            c.setUplift(treatRate.subtract(ctrlRate).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        } else {
            c.setUplift(null);
        }
    }

    /**
     * Đo chuyển đổi cho chiến dịch có mục tiêu AUTO_OCS. Cửa sổ ghi nhận (SRS 3.2.6.1: "N ngày kể từ khi chiến dịch
     * chạy") tính riêng cho từng khách hàng từ lúc lượt chạy đưa họ vào chiến dịch (TARGET.CREATED_AT), nên chiến dịch
     * lên lịch xa ngày duyệt và các lượt sau của chiến dịch lặp lại vẫn được đo.
     */
    public int measureConversions() {
        int total = 0;
        Instant now = Instant.now();
        Set<Status> statuses = EnumSet.of(Status.RUNNING, Status.PAUSED, Status.COMPLETED);
        for (Campaign c : campaignRepo.findByStatusIn(statuses)) {
            CampaignDefinition def = campaignService.definitionOf(c);
            if (def.goal() == null || !"AUTO_OCS".equals(def.goal().mode())) {
                continue;
            }
            int window = Math.max(1, def.goal().attributionWindowDays());
            if (c.getCompletedAt() != null && now.isAfter(c.getCompletedAt().plus(Duration.ofDays(window + 1L)))) {
                continue;
            }
            total += measure(c, def, now.minus(Duration.ofDays(window)));
        }
        return total;
    }

    private int measure(Campaign c, CampaignDefinition def, Instant windowStart) {
        Instant cutoff = Instant.now().minus(CONVERSION_MIN_AGE);
        List<ConversionSource.Candidate> candidates = jdbc.query(
                "SELECT * FROM (SELECT t.ID, t.ISDN, t.IS_CONTROL FROM LADS_CAMPAIGN_TARGET t WHERE t.CAMPAIGN_ID = ? "
                        + "AND t.CONVERTED = 0 AND t.CREATED_AT <= ? AND t.CREATED_AT >= ? AND (t.IS_CONTROL = 1 OR EXISTS "
                        + "(SELECT 1 FROM LADS_CAMPAIGN_MESSAGE m WHERE m.TARGET_ID = t.ID AND m.STATUS IN ('SENT', 'DELIVERED') "
                        + "AND m.SENT_AT <= ?)) ORDER BY t.ID) WHERE ROWNUM <= ?",
                (rs, i) -> new ConversionSource.Candidate(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1),
                c.getId(), Utc.ts(cutoff), Utc.ts(windowStart), Utc.ts(cutoff), CONVERSION_BATCH);
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<Long> converted = conversionSource.converted(c.getId(), def.goal(), candidates);
        if (converted.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = converted.stream().map(id -> new Object[]{Utc.ts(Instant.now()), id}).toList();
        jdbc.batchUpdate("UPDATE LADS_CAMPAIGN_TARGET SET CONVERTED = 1, CONVERTED_AT = ? WHERE ID = ? AND CONVERTED = 0", rows);
        return converted.size();
    }

    /** Mục tiêu MANUAL_FILE (SRS 3.2.6.2): đánh dấu chuyển đổi theo danh sách số thuê bao tải lên. */
    @Transactional
    public int importConversions(long campaignId, java.util.Collection<String> isdns) {
        List<Object[]> rows = isdns.stream().map(i -> new Object[]{Utc.ts(Instant.now()), campaignId, i}).toList();
        // Đếm trước / sau: Oracle JDBC có thể trả SUCCESS_NO_INFO (-2) cho từng lệnh trong batch thay vì số dòng
        String countSql = "SELECT COUNT(*) FROM LADS_CAMPAIGN_TARGET WHERE CAMPAIGN_ID = ? AND CONVERTED = 1";
        Long before = jdbc.queryForObject(countSql, Long.class, campaignId);
        jdbc.batchUpdate("UPDATE LADS_CAMPAIGN_TARGET SET CONVERTED = 1, CONVERTED_AT = ? "
                + "WHERE CAMPAIGN_ID = ? AND ISDN = ? AND CONVERTED = 0", rows, 1000, (ps, row) -> {
            ps.setObject(1, row[0]);
            ps.setObject(2, row[1]);
            ps.setObject(3, row[2]);
        });
        Long after = jdbc.queryForObject(countSql, Long.class, campaignId);
        int n = (int) Math.max(0, (after == null ? 0 : after) - (before == null ? 0 : before));
        campaignRepo.findById(campaignId).ifPresent(this::refresh);
        return n;
    }
}
