package com.unitel.laoads.web.bootstrap;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.BudgetDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.GoalDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.SilentHoursDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Mode;
import com.unitel.laoads.domain.campaign.CampaignEnums.Type;
import com.unitel.laoads.domain.campaign.CampaignGroup;
import com.unitel.laoads.domain.campaign.CampaignInput;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.segment.Segment;
import com.unitel.laoads.domain.segment.SegmentCondition;
import com.unitel.laoads.domain.segment.SegmentService;
import com.unitel.laoads.domain.settings.SenderKind;
import com.unitel.laoads.domain.settings.SenderNumberRepository;
import com.unitel.laoads.domain.settings.SettingsService;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceRepository;
import com.unitel.laoads.infra.bootstrap.DatabaseReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Dữ liệu cho engine ở DB LOCAL dev (chỉ profile dev, sau DevMockDataSeeder): 30.000 thuê bao giả lập trong
 * LADS_SUBSCRIBER (thay cho dữ liệu trích xuất BI), tệp KH QUERY / FILE, nhóm chiến dịch và chiến dịch ở đủ
 * trạng thái. Chiến dịch đã duyệt được engine chạy thật qua gateway giả lập → báo cáo / dashboard có số liệu.
 * Idempotent: mỗi phần chỉ chạy khi phần đó còn trống. Tắt cùng mock data: LAOADS_DEV_MOCK_DATA=false.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "laoads.dev.mock-data", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DevEngineDataSeeder {

    static final int SUBSCRIBERS = 30_000;
    private static final String[] PROVINCES = {"VTE", "VTE", "VTE", "LPB", "SVK", "SVK", "CPS", "XKH", "OUD", "BOK"};
    private static final String[] PACKAGES = {"D5K", "D10K", "M50", "M99", "M150", "NONE"};

    private final JdbcTemplate jdbc;
    private final WorkspaceRepository workspaceRepo;
    private final SenderNumberRepository senderRepo;
    private final SettingsService settingsService;
    private final SegmentService segmentService;
    private final CampaignService campaignService;
    private final CampaignRepository campaignRepo;

    @Order(20)
    @EventListener(DatabaseReadyEvent.class)
    public void onDatabaseReady() {
        try {
            int subs = seedSubscribers();
            if (senderRepo.findByCode("1555").isEmpty()) {
                settingsService.createSender("1555", SenderKind.SHORTCODE, "Dịch vụ giá trị gia tăng", true, "seed");
            }
            Workspace bsd = workspaceRepo.findByCode("BSD").orElse(null);
            if (bsd == null) {
                return;
            }
            int campaigns = campaignRepo.countByWorkspaceId(bsd.getId()) > 0 ? 0 : seedCampaigns(bsd.getId());
            log.info("[MOCK] Dữ liệu engine: +{} thuê bao, +{} chiến dịch", subs, campaigns);
        } catch (Exception e) {
            log.warn("[A++] Nạp dữ liệu engine dev bị bỏ qua: {}", e.getMessage(), e);
        }
    }

    // ───────────── Thuê bao giả lập (staging BI) ─────────────

    private int seedSubscribers() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM LADS_SUBSCRIBER", Integer.class);
        if (n != null && n > 0) {
            return 0;
        }
        Random r = new Random(42);
        Set<String> seen = new HashSet<>();
        Date today = Date.valueOf(LocalDate.now());
        List<Object[]> batch = new ArrayList<>();
        int inserted = 0;
        while (inserted < SUBSCRIBERS) {
            String isdn = r.nextInt(10) < 8 ? "85620" + (50_000_000 + r.nextInt(49_999_999)) : "85630" + (2_000_000 + r.nextInt(7_999_999));
            if (!seen.add(isdn)) {
                continue;
            }
            int deviceRoll = r.nextInt(100);
            String device = deviceRoll < 72 ? "SMARTPHONE" : deviceRoll < 95 ? "FEATURE_PHONE" : "MODEM";
            boolean smart = "SMARTPHONE".equals(device);
            boolean lte = smart ? r.nextInt(100) < 85 : r.nextInt(100) < 10;
            int actRoll = r.nextInt(100);
            String act = actRoll < 85 ? "00" : actRoll < 92 ? "01" : actRoll < 97 ? "02" : "03";
            String pkg = PACKAGES[r.nextInt(PACKAGES.length)];
            String dataPkg = pkg.startsWith("D") ? (r.nextInt(10) < 8 ? "DAY" : "WEEK") : pkg.startsWith("M") ? "MONTH" : "NONE";
            double volume = r.nextInt(100) < 18 ? 0 : Math.round(Math.exp(r.nextGaussian() * 0.9 + 1.6) * 10) / 10.0;
            batch.add(new Object[]{isdn, today, PROVINCES[r.nextInt(PROVINCES.length)], 16 + r.nextInt(55),
                    r.nextBoolean() ? "M" : "F", 1 + r.nextInt(180), act, device, lte ? 1 : 0, smart ? 1 : 0, pkg, dataPkg,
                    Math.min(volume, 60), r.nextInt(100) < 5 ? 0 : 5_000 + r.nextInt(145_000), Math.round(r.nextDouble() * 200) / 10.0,
                    r.nextInt(100) < 40 ? 0 : r.nextInt(30), r.nextInt(100) < 35 ? 1 : 0, r.nextInt(200_000)});
            inserted++;
            if (batch.size() == 1000) {
                insertSubscribers(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            insertSubscribers(batch);
        }
        return inserted;
    }

    private void insertSubscribers(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO LADS_SUBSCRIBER (ISDN, SNAPSHOT_DAY, PROVINCE_CODE_ACTIVE, CUSTOMER_AGE, GENDER, TENURE, "
                + "ACT_STATUS, DEVICE_TYPE, INFRAS_DATA_4G, IS_SMARTPHONE, REAL_PACKAGE_DATA, DATA_PACKAGE, G_VOLUME, "
                + "T_ORG_CHARGE, LAOSTV_TOTAL_VOLUME, LOGIN_SUPERAPP, ACTIVE_UMONEY, BALANCE_BASIC) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", batch);
    }

    // ───────────── Tệp KH + nhóm + chiến dịch ─────────────

    private int seedCampaigns(long ws) {
        Map<String, Long> groups = new HashMap<>();
        for (String name : List.of("Phát triển thuê bao", "Upsell gói data", "Chéo BU")) {
            CampaignGroup g = campaignService.groups(ws).stream().map(CampaignService.GroupView::group)
                    .filter(x -> x.getName().equals(name)).findFirst()
                    .orElseGet(() -> campaignService.createGroup(ws, name, "owner_bsd"));
            groups.put(name, g.getId());
        }
        Segment lte = query(ws, "Thuê bao 4G chưa dùng data – T11/2026",
                "CAMP_5: máy hỗ trợ 4G nhưng không phát sinh lưu lượng data",
                List.of(c("act_status", "EQ", "00", null, "AND"), c("infras_data_4g", "EQ", "true", null, "AND"),
                        c("g_volume", "EQ", "0", null, "AND")));
        Segment upsell = query(ws, "Gói ngày → nâng cấp gói tháng (30K–50K Kip)",
                "CAMP_4: cước gốc 30K–50K, lưu lượng 5–15GB, chưa có gói tháng",
                List.of(c("t_org_charge", "BETWEEN", "30000", "50000", "AND"), c("g_volume", "BETWEEN", "5", "15", "AND"),
                        c("data_package", "NEQ", "MONTH", null, "AND")));
        Segment umoney = query(ws, "Chưa kích hoạt U-Money – tỉnh Viêng Chăn", "CAMP_1: có phát sinh cước, chưa có U-Money",
                List.of(c("active_umoney", "EQ", "false", null, "AND"), c("t_org_charge", "GT", "0", null, "AND"),
                        c("province_code_active", "EQ", "VTE", null, "AND")));
        List<String> vip = jdbc.queryForList("SELECT ISDN FROM (SELECT ISDN FROM LADS_SUBSCRIBER WHERE PROVINCE_CODE_ACTIVE = "
                + "'VTE' ORDER BY BALANCE_BASIC DESC) WHERE ROWNUM <= 400", String.class);
        String csv = "isdn\n" + String.join("\n", vip);
        segmentService.importFile(ws, "Danh sách VIP Viêng Chăn (file)", null, "vip_vientiane_2026-11.csv",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "member_bsd");

        Instant now = Instant.now();
        List<String> testIsdns = vip.subList(0, Math.min(3, vip.size()));
        GoalDef autoGoal = new GoalDef("AUTO_OCS", "Phát sinh lưu lượng data 4G trong 7 ngày", 7, true, null);
        int n = 0;

        // 1) Đang chạy: A/B (SMS → tặng data / WhatsApp có link tracking) + nhóm đối chứng
        Campaign c1 = campaignService.create(ws, new CampaignInput("Phát triển thuê bao Data 4G – Vòng 1",
                "Kích thích thuê bao máy 4G dùng data lần đầu", Mode.OFFICIAL, Type.STATIC, groups.get("Phát triển thuê bao"),
                lte.getId(), "LO", List.of(), new CampaignDefinition(List.of(
                new ActionGroupDef("ga", "Nhóm A – SMS + tặng data", 45, false, "SEQUENTIAL", List.of(
                        sms("a1", "Unitel tặng bạn 1GB data 4G! Trải nghiệm tốc độ cao ngay hôm nay.", 0, false),
                        new ActionDef("a2", ActionType.ADD_DATA, 1, null, null, null, null, BigDecimal.valueOf(1024), "DATA1GB", false))),
                new ActionGroupDef("gb", "Nhóm B – WhatsApp", 45, false, "SEQUENTIAL", List.of(
                        new ActionDef("b1", ActionType.NOTIFICATION_WHATSAPP, 0, null, null,
                                "Kích hoạt 4G miễn phí 3 ngày: https://unitel.com.la/4g", "promo_4g", null, null, true))),
                new ActionGroupDef("gc", "Nhóm đối chứng", 10, true, "SEQUENTIAL", List.of())),
                new ScheduleDef("NOW", now, null, null, null, null, null, new SilentHoursDef(false, "21:00", "07:00")),
                autoGoal, new BudgetDef(null, null))), true, "admin_bsd");
        campaignService.approve(ws, c1.getId(), "owner_bsd");
        n++;

        // 2) Đã lên lịch: lặp lại hằng tuần (Thứ 2, Thứ 5 lúc 09:00) trong 30 ngày
        Instant tomorrow = now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS);
        Campaign c2 = campaignService.create(ws, new CampaignInput("Nâng cấp gói tháng cho khách dùng gói ngày",
                "Mời chuyển sang gói tháng M99", Mode.OFFICIAL, Type.STATIC, groups.get("Upsell gói data"), upsell.getId(), "LO",
                List.of(), new CampaignDefinition(List.of(
                new ActionGroupDef("g1", "Toàn bộ", 100, false, "SEQUENTIAL", List.of(
                        sms("u1", "Chuyển sang gói tháng M99 chỉ 99.000 Kip — nhiều data hơn, tiết kiệm hơn. Soạn M99 gửi 118.", 0, false)))),
                new ScheduleDef("RECURRING", tomorrow, tomorrow.plus(Duration.ofDays(30)), 1, "WEEK", List.of(1, 4), "09:00",
                        new SilentHoursDef(false, "21:00", "07:00")),
                new GoalDef("NONE", "", 7, false, null), new BudgetDef(null, BigDecimal.valueOf(5_000_000)))), true, "owner_bsd");
        campaignService.approve(ws, c2.getId(), "owner_bsd");
        n++;

        // 3) Chờ duyệt, 4) Bị từ chối, 5) Nháp
        campaignService.create(ws, simple("Ưu đãi LaoTV cuối năm", upsell.getId(), groups.get("Upsell gói data"),
                ActionType.NOTIFICATION_SMS, "Xem LaoTV miễn phí 7 ngày dịp cuối năm!"), true, "admin_bsd");
        Campaign c4 = campaignService.create(ws, simple("Khuyến mãi nạp thẻ cuối tuần", umoney.getId(), null,
                ActionType.NOTIFICATION_SMS, "Nạp thẻ cuối tuần nhận thêm 20%."), true, "member_bsd");
        campaignService.reject(ws, c4.getId(), "Trùng thời gian với chương trình khuyến mãi tập đoàn", "owner_bsd");
        campaignService.create(ws, simple("Đăng ký Super App tặng 1GB", lte.getId(), groups.get("Phát triển thuê bao"),
                ActionType.NOTIFICATION_WHATSAPP, "Tải Unitel Super App, nhận ngay 1GB."), false, "member_bsd");
        n += 3;

        // 6) Thử nghiệm với danh sách số cố định → chạy xong nhanh
        Campaign c6 = campaignService.create(ws, new CampaignInput("Kiểm thử nội dung SMS khuyến mãi", null, Mode.TEST,
                Type.STATIC, null, null, "LO", testIsdns, new CampaignDefinition(List.of(
                new ActionGroupDef("t1", "Thử nghiệm", 100, false, "SEQUENTIAL", List.of(
                        sms("t1a", "[TEST] Unitel khuyến mãi 50% gói data tháng: https://unitel.com.la/km", 0, true)))),
                new ScheduleDef("NOW", now, null, null, null, null, null, null), new GoalDef("NONE", "", 1, false, null),
                new BudgetDef(null, null))), true, "admin_bsd");
        campaignService.approve(ws, c6.getId(), "owner_bsd");
        n++;

        // 7) Theo sự kiện: kích hoạt SIM (chờ sự kiện qua API)
        Campaign c7 = campaignService.create(ws, new CampaignInput("Chào mừng thuê bao mới (D0)", "EVT:SIM_ACTIVATION_D0",
                Mode.OFFICIAL, Type.EVENT, groups.get("Phát triển thuê bao"), null, "LO", List.of(), new CampaignDefinition(List.of(
                new ActionGroupDef("e1", "Toàn bộ", 100, false, "SEQUENTIAL", List.of(
                        sms("e1a", "Chào mừng bạn đến với Unitel! Tặng 500MB data cho ngày đầu tiên.", 0, false),
                        new ActionDef("e1b", ActionType.ADD_DATA, 0, null, null, null, null, BigDecimal.valueOf(500), "DATA500", false)))),
                null, new GoalDef("NONE", "", 1, false, null), new BudgetDef(null, null))), true, "admin_bsd");
        campaignService.approve(ws, c7.getId(), "owner_bsd");
        n++;

        // 8) Chéo BU U-Money — chạy ngay, có mục tiêu chuyển đổi
        Campaign c8 = campaignService.create(ws, new CampaignInput("Chéo BU U-Money – Đợt 1", "Mời mở ví U-Money",
                Mode.OFFICIAL, Type.STATIC, groups.get("Chéo BU"), umoney.getId(), "LO", List.of(), new CampaignDefinition(List.of(
                new ActionGroupDef("m1", "Nhận tin", 90, false, "SEQUENTIAL", List.of(
                        sms("m1a", "Mở ví U-Money hôm nay nhận ngay 10.000 Kip: https://umoney.la/open", 0, true))),
                new ActionGroupDef("m2", "Nhóm đối chứng", 10, true, "SEQUENTIAL", List.of())),
                new ScheduleDef("NOW", now, null, null, null, null, null, null),
                new GoalDef("AUTO_OCS", "Mở ví U-Money (active_umoney = 1)", 14, true, null), new BudgetDef(null, null))),
                true, "admin_bsd");
        campaignService.approve(ws, c8.getId(), "owner_bsd");
        n++;
        return n;
    }

    private Segment query(long ws, String name, String description, List<SegmentCondition> conditions) {
        Segment s = segmentService.createQuery(ws, name, description, conditions, "admin_bsd");
        segmentService.buildQuerySegment(s.getId());
        return segmentService.get(ws, s.getId());
    }

    private static SegmentCondition c(String field, String op, String value, String valueTo, String join) {
        return new SegmentCondition(field, op, value, valueTo, join);
    }

    private static ActionDef sms(String id, String content, int delay, boolean track) {
        return new ActionDef(id, ActionType.NOTIFICATION_SMS, delay, "118", null, content, null, null, null, track);
    }

    private static CampaignInput simple(String name, Long segmentId, Long groupId, ActionType type, String content) {
        String sender = type == ActionType.NOTIFICATION_SMS ? "118" : null;
        return new CampaignInput(name, null, Mode.OFFICIAL, Type.STATIC, groupId, segmentId, "LO", List.of(),
                new CampaignDefinition(List.of(new ActionGroupDef("g1", "Toàn bộ", 100, false, "SEQUENTIAL",
                        List.of(new ActionDef("a1", type, 0, sender, null, content, null, null, null, false)))),
                        new ScheduleDef("ONCE", Instant.now().plus(Duration.ofDays(3)), Instant.now().plus(Duration.ofDays(10)),
                                null, null, null, null, null),
                        new GoalDef("NONE", "", 7, false, null), new BudgetDef(null, null)));
    }
}
