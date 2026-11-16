package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Mode;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignEnums.Type;
import com.unitel.laoads.domain.campaign.CampaignHistory;
import com.unitel.laoads.domain.campaign.CampaignHistoryRepository;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.campaign.SchedulePlanner;
import com.unitel.laoads.domain.settings.NotificationSettings;
import com.unitel.laoads.domain.settings.SenderNumber;
import com.unitel.laoads.domain.settings.SenderNumberRepository;
import com.unitel.laoads.domain.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Engine — khởi chạy lượt (run) của chiến dịch: chốt danh sách đối tượng, chia nhóm A/B + nhóm đối chứng,
 * sinh tin theo chuỗi hành động (tuần tự có thời gian chờ / song song), dời giờ gửi khỏi khung giờ không gửi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignRunner {

    /** Một nhóm A/B với khoảng bucket [from, to) trên thang 0..99. */
    record GroupRange(String key, boolean control, int from, int to, ActionGroupDef def) {
    }

    private final CampaignRepository campaignRepo;
    private final CampaignHistoryRepository historyRepo;
    private final CampaignService campaignService;
    private final SettingsService settingsService;
    private final SenderNumberRepository senderRepo;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Long> dueCampaigns(Instant now) {
        return campaignRepo.dueForRun(EnumSet.of(Status.SCHEDULED, Status.RUNNING), now);
    }

    /** Chạy lượt kế tiếp nếu chiến dịch tới hạn. Trả về số đối tượng của lượt (0 nếu không chạy). */
    @Transactional
    public int startRun(long campaignId) {
        Campaign c = campaignRepo.findById(campaignId).orElse(null);
        Instant now = Instant.now();
        if (c == null || c.getType() == Type.EVENT || c.getNextRunAt() == null || c.getNextRunAt().isAfter(now)
                || !(c.is(Status.SCHEDULED) || c.is(Status.RUNNING))) {
            return 0;
        }
        CampaignDefinition def = campaignService.definitionOf(c);
        Instant scheduledAt = c.getNextRunAt();
        if (c.getEndAt() != null && scheduledAt.isAfter(c.getEndAt())) {
            c.setNextRunAt(null);
            return 0;
        }
        int runNo = c.getRunCount() + 1;
        long runId = nextVal("LADS_CAMPAIGN_RUN_SEQ");
        jdbc.update("INSERT INTO LADS_CAMPAIGN_RUN (ID, CAMPAIGN_ID, RUN_NO, STATUS, SCHEDULED_AT, STARTED_AT, TARGET_COUNT) "
                + "VALUES (?, ?, ?, 'RUNNING', ?, ?, 0)", runId, campaignId, runNo, Utc.ts(scheduledAt), Utc.ts(now));

        List<GroupRange> ranges = ranges(def);
        int targets = materializeTargets(c, ranges, runId, now);
        createMessages(c, def, ranges, "t.RUN_ID = ?", runId, now);
        jdbc.update("UPDATE LADS_CAMPAIGN_RUN SET TARGET_COUNT = ? WHERE ID = ?", targets, runId);

        c.setRunCount(runNo);
        c.setStatus(Status.RUNNING);
        c.setNextRunAt(SchedulePlanner.nextAfter(def.schedule(), scheduledAt));
        c.setUpdatedAt(now);
        historyRepo.save(CampaignHistory.of(campaignId, "system", "START",
                "Lượt #" + runNo + ": " + targets + " thuê bao"));
        log.info("[ENGINE] Chiến dịch #{} bắt đầu lượt #{} — {} thuê bao, lượt kế tiếp: {}", campaignId, runNo, targets,
                c.getNextRunAt());
        return targets;
    }

    /** Thêm 1 thuê bao vào lượt "luồng sự kiện" của chiến dịch EVENT. Trả về false nếu đã có trong lượt. */
    @Transactional
    public boolean addEventTarget(long campaignId, String isdn) {
        Campaign c = campaignRepo.findById(campaignId).orElse(null);
        if (c == null || !c.is(Status.RUNNING) || c.getType() != Type.EVENT) {
            return false;
        }
        Instant now = Instant.now();
        CampaignDefinition def = campaignService.definitionOf(c);
        Long runId = jdbc.query("SELECT ID FROM LADS_CAMPAIGN_RUN WHERE CAMPAIGN_ID = ? AND RUN_NO = 1",
                rs -> rs.next() ? rs.getLong(1) : null, campaignId);
        if (runId == null) {
            runId = nextVal("LADS_CAMPAIGN_RUN_SEQ");
            jdbc.update("INSERT INTO LADS_CAMPAIGN_RUN (ID, CAMPAIGN_ID, RUN_NO, STATUS, SCHEDULED_AT, STARTED_AT, "
                    + "TARGET_COUNT) VALUES (?, ?, 1, 'RUNNING', ?, ?, 0)", runId, campaignId, Utc.ts(now), Utc.ts(now));
            c.setRunCount(1);
            historyRepo.save(CampaignHistory.of(campaignId, "system", "START", "Bắt đầu nhận sự kiện"));
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM LADS_CAMPAIGN_TARGET WHERE RUN_ID = ? AND ISDN = ?",
                Integer.class, runId, isdn);
        if (exists != null && exists > 0) {
            return false;
        }
        List<GroupRange> ranges = ranges(def);
        GroupRange g = rangeOf(ranges, bucket(isdn, campaignId));
        long targetId = nextVal("LADS_CAMPAIGN_TARGET_SEQ");
        jdbc.update("INSERT INTO LADS_CAMPAIGN_TARGET (ID, RUN_ID, CAMPAIGN_ID, ISDN, GROUP_KEY, IS_CONTROL, CONVERTED, "
                + "CREATED_AT) VALUES (?, ?, ?, ?, ?, ?, 0, ?)", targetId, runId, campaignId, isdn, g.key(),
                g.control() ? 1 : 0, Utc.ts(now));
        createMessages(c, def, List.of(g), "t.ID = ?", targetId, now);
        jdbc.update("UPDATE LADS_CAMPAIGN_RUN SET TARGET_COUNT = TARGET_COUNT + 1 WHERE ID = ?", runId);
        c.setTargetCount(c.getTargetCount() + 1);
        return true;
    }

    // ───────────── đối tượng ─────────────

    static List<GroupRange> ranges(CampaignDefinition def) {
        List<GroupRange> out = new ArrayList<>();
        int from = 0;
        List<ActionGroupDef> groups = def.groups();
        for (int i = 0; i < groups.size(); i++) {
            ActionGroupDef g = groups.get(i);
            int to = Math.min(100, from + Math.max(0, g.allocationPercent()));
            String key = g.id() == null || g.id().isBlank() ? "g" + i : g.id();
            if (to > from) {
                out.add(new GroupRange(key, g.isControl(), from, to, g));
            }
            from = to;
        }
        if (out.isEmpty() || out.get(out.size() - 1).to() < 100) {
            // phân bổ không đủ 100 (dữ liệu cũ) → phần còn lại vào nhóm cuối cùng
            GroupRange last = out.isEmpty() ? null : out.remove(out.size() - 1);
            out.add(last == null ? new GroupRange("g0", false, 0, 100, groups.isEmpty() ? null : groups.get(0))
                    : new GroupRange(last.key(), last.control(), last.from(), 100, last.def()));
        }
        return out;
    }

    static int bucket(String isdn, long campaignId) {
        return Math.floorMod((isdn + ":" + campaignId).hashCode(), 100);
    }

    static GroupRange rangeOf(List<GroupRange> ranges, int bucket) {
        for (GroupRange r : ranges) {
            if (bucket >= r.from() && bucket < r.to()) {
                return r;
            }
        }
        return ranges.get(ranges.size() - 1);
    }

    private int materializeTargets(Campaign c, List<GroupRange> ranges, long runId, Instant now) {
        List<String> testIsdns = c.getTestIsdns() == null ? List.of() : List.of(c.getTestIsdns().split(","));
        if (c.getMode() == Mode.TEST && !testIsdns.isEmpty()) {
            List<Object[]> batch = new ArrayList<>();
            for (String isdn : testIsdns) {
                GroupRange g = rangeOf(ranges, bucket(isdn, c.getId()));
                batch.add(new Object[]{runId, c.getId(), isdn, g.key(), g.control() ? 1 : 0, Utc.ts(now)});
            }
            jdbc.batchUpdate("INSERT INTO LADS_CAMPAIGN_TARGET (ID, RUN_ID, CAMPAIGN_ID, ISDN, GROUP_KEY, IS_CONTROL, "
                    + "CONVERTED, CREATED_AT) VALUES (LADS_CAMPAIGN_TARGET_SEQ.NEXTVAL, ?, ?, ?, ?, ?, 0, ?)", batch);
            return batch.size();
        }
        if (c.getSegmentId() == null) {
            return 0;
        }
        // bucket = MOD(ORA_HASH(isdn || ':' || campaignId), 100) — chia nhóm tất định trong SQL (không kéo về JVM)
        StringBuilder keyCase = new StringBuilder("CASE");
        StringBuilder ctlCase = new StringBuilder("CASE");
        List<Object> params = new ArrayList<>(List.of(runId, c.getId()));
        List<Object> ctlParams = new ArrayList<>();
        for (GroupRange r : ranges) {
            keyCase.append(" WHEN x.B < ? THEN ?");
            params.add(r.to());
            params.add(r.key());
            ctlCase.append(" WHEN x.B < ? THEN ").append(r.control() ? 1 : 0);
            ctlParams.add(r.to());
        }
        keyCase.append(" END");
        ctlCase.append(" ELSE 0 END");
        params.addAll(ctlParams);
        params.add(Utc.ts(now));
        params.add(":" + c.getId());
        params.add(c.getSegmentId());
        String source = c.getMode() == Mode.TEST
                ? "SELECT ISDN FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ? AND ROWNUM <= " + CampaignService.TEST_LIMIT
                : "SELECT ISDN FROM LADS_SEGMENT_MEMBER WHERE SEGMENT_ID = ?";
        String sql = "INSERT INTO LADS_CAMPAIGN_TARGET (ID, RUN_ID, CAMPAIGN_ID, ISDN, GROUP_KEY, IS_CONTROL, CONVERTED, "
                + "CREATED_AT) SELECT LADS_CAMPAIGN_TARGET_SEQ.NEXTVAL, ?, ?, x.ISDN, " + keyCase + ", " + ctlCase
                + ", 0, ? FROM (SELECT s.ISDN, MOD(ORA_HASH(s.ISDN || ?), 100) B FROM (" + source + ") s) x";
        return jdbc.update(sql, params.toArray());
    }

    // ───────────── tin gửi ─────────────

    private void createMessages(Campaign c, CampaignDefinition def, List<GroupRange> ranges, String targetFilter,
                                long filterValue, Instant base) {
        NotificationSettings global = settingsService.notification();
        Map<String, SenderNumber> senders = SenderPolicy.byCode(senderRepo.findAll());
        for (GroupRange r : ranges) {
            if (r.control() || r.def() == null || r.def().actions() == null) {
                continue;
            }
            long cumulative = 0;
            String prevKey = null;
            List<ActionDef> actions = r.def().actions();
            for (int i = 0; i < actions.size(); i++) {
                ActionDef a = actions.get(i);
                if (a.type() == null) {
                    continue;
                }
                cumulative = r.def().sequential() ? cumulative + Math.max(0, a.delayMinutes()) : Math.max(0, a.delayMinutes());
                Instant due = base.plus(Duration.ofMinutes(cumulative));
                // khung giờ không gửi theo đầu số của từng hành động (chiến dịch > đầu số > chung)
                LocalTime[] silent = SenderPolicy.silentWindow(def, senders.get(SenderPolicy.key(a.senderId())), global);
                if (silent != null) {
                    due = SchedulePlanner.applySilentHours(due, silent[0], silent[1]);
                }
                String actionKey = a.id() == null || a.id().isBlank() ? "a" + i : a.id();
                jdbc.update("INSERT INTO LADS_CAMPAIGN_MESSAGE (ID, CAMPAIGN_ID, RUN_ID, TARGET_ID, ISDN, GROUP_KEY, "
                                + "ACTION_KEY, ACTION_TYPE, CHANNEL, STATUS, ATTEMPTS, DUE_AT, COST, REFUNDED) "
                                + "SELECT LADS_CAMPAIGN_MESSAGE_SEQ.NEXTVAL, ?, t.RUN_ID, t.ID, t.ISDN, t.GROUP_KEY, ?, ?, ?, "
                                + "'PENDING', 0, ?, 0, 0 FROM LADS_CAMPAIGN_TARGET t WHERE " + targetFilter
                                + " AND t.GROUP_KEY = ?",
                        c.getId(), actionKey, a.type().name(), a.channel().name(), Utc.ts(due), filterValue, r.key());
                if (r.def().sequential() && prevKey != null) {
                    jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE m SET PREV_MESSAGE_ID = (SELECT p.ID FROM LADS_CAMPAIGN_MESSAGE p "
                                    + "WHERE p.TARGET_ID = m.TARGET_ID AND p.ACTION_KEY = ? AND ROWNUM = 1) "
                                    + "WHERE m.CAMPAIGN_ID = ? AND m.ACTION_KEY = ? AND m.STATUS = 'PENDING' "
                                    + "AND m.PREV_MESSAGE_ID IS NULL AND m.TARGET_ID IN (SELECT t.ID FROM LADS_CAMPAIGN_TARGET t WHERE "
                                    + targetFilter + ")",
                            prevKey, c.getId(), actionKey, filterValue);
                }
                prevKey = actionKey;
            }
        }
    }

    /** Khung giờ không gửi khi hành động không gắn đầu số: của chiến dịch nếu bật, ngược lại mặc định hệ thống. */
    static LocalTime[] silentWindow(CampaignDefinition def, NotificationSettings global) {
        return SenderPolicy.silentWindow(def, null, global);
    }

    private long nextVal(String sequence) {
        Long v = jdbc.queryForObject("SELECT " + sequence + ".NEXTVAL FROM DUAL", Long.class);
        if (v == null) {
            throw new IllegalStateException("Không lấy được " + sequence);
        }
        return v;
    }
}
