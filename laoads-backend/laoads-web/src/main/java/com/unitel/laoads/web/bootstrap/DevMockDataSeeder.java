package com.unitel.laoads.web.bootstrap;

import com.unitel.laoads.domain.audit.AuditWriter;
import com.unitel.laoads.domain.audit.AuditWriter.AuditEvent;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.pricing.PriceItemCommand;
import com.unitel.laoads.domain.pricing.PriceQuote;
import com.unitel.laoads.domain.pricing.PriceQuoteRepository;
import com.unitel.laoads.domain.pricing.PriceQuoteService;
import com.unitel.laoads.domain.pricing.PriceQuoteStatus;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserStatus;
import com.unitel.laoads.domain.workspace.MemberRole;
import com.unitel.laoads.domain.workspace.MemberStatus;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceMember;
import com.unitel.laoads.domain.workspace.WorkspaceMemberRepository;
import com.unitel.laoads.domain.workspace.WorkspaceRepository;
import com.unitel.laoads.domain.workspace.WorkspaceStatus;
import com.unitel.laoads.infra.bootstrap.DatabaseReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock data cho DB LOCAL của dev — CHỈ profile dev (không uat: uat có thể trỏ DB dùng chung với BOC).
 *
 * <p>Chạy sau {@link DevDataSeeder} và bổ sung dữ liệu giống MSW của FE ({@code laoads-web/src/mocks/db.ts}) để
 * các màn hình core ở chế độ hybrid (người dùng, workspace, thành viên, bảng giá, nhật ký) có dữ liệu thật trong DB.
 * Chiến dịch / tệp KH chưa có bảng ở BE nên vẫn do MSW mock.
 *
 * <p>Idempotent theo khoá tự nhiên (username, mã workspace, cặp workspace–user, tên bảng giá). 4 tài khoản gốc và
 * BSD/BCD chỉ được "làm giàu" (tên thật, SĐT, ngày tạo lùi) khi còn nguyên giá trị của seeder. Nhật ký chỉ nạp
 * một lần (đánh dấu {@code REQUEST_ID = mock-seed}). Mật khẩu mọi tài khoản mock mới: {@value #MOCK_PASSWORD}.
 * Tắt: {@code LAOADS_DEV_MOCK_DATA=false}. Nạp lại từ đầu: {@code docker compose -f docker-compose.dev.yml down -v}.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "laoads.dev.mock-data", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DevMockDataSeeder {

    static final String SEED_ACTOR = "seed";
    static final String AUDIT_MARKER = "mock-seed";
    static final String MOCK_PASSWORD = "member";
    static final String Q3 = "Bảng giá Q3/2026";
    static final String Q4 = "Bảng giá Q4/2026";
    static final String Q1_DRAFT = "Bảng giá Q1/2027 (dự thảo)";

    private static final ZoneId VTE = ZoneId.of("Asia/Vientiane");
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    record UserSpec(String username, String fullName, String email, String phone, SystemRole role,
                    UserStatus status, int createdDaysAgo, Integer lastLoginDaysAgo) {
    }

    record WsSpec(String code, String name, String description, WorkspaceStatus status, String contactName,
                  String contactEmail, String contactPhone, int createdDaysAgo) {
    }

    record MemberSpec(String code, String username, MemberRole role, int joinedDaysAgo, Integer revokedDaysAgo) {
    }

    /** 4 tài khoản của DevDataSeeder — chỉ cập nhật thông tin hiển thị khi còn nguyên giá trị seed. */
    static final List<UserSpec> BASE_USERS = List.of(
            user("superadmin", "Quản trị hệ thống", "admin@unitel.com.la", "8562055500001", SystemRole.SUPER_ADMIN, 120, 0),
            user("owner_bsd", "Souksavanh Phommachanh", "souksavanh.p@unitel.com.la", "8562055510002", 110, 1),
            user("admin_bsd", "Nguyễn Thu Hà", "ha.nt@unitel.com.la", "8562055520003", 100, 2),
            user("member_bsd", "Khamphone Sisouk", "khamphone.s@unitel.com.la", "8562055530004", 90, 0));

    static final List<UserSpec> EXTRA_USERS = List.of(
            user("vilay.k", "Vilayphone Keomany", "vilay.k@unitel.com.la", "8562055540005", 60, 6),
            new UserSpec("tran.minh", "Trần Văn Minh", "minh.tv@unitel.com.la", "8562055550006", SystemRole.USER,
                    UserStatus.LOCKED, 80, 40),
            user("noy.s", "Noy Saysana", "noy.s@unitel.com.la", "8562055560007", 5, null),
            user("chanthala.p", "Chanthala Phimmasone", "chanthala.p@unitel.com.la", "8562055570008", 75, 0),
            user("bounmy.v", "Bounmy Vongsavath", "bounmy.v@unitel.com.la", "8562055580009", 70, 3),
            user("phouvong.s", "Phouvong Sengdara", "phouvong.s@unitel.com.la", "8562055590010", 50, 4),
            user("hoang.nam", "Hoàng Văn Nam", "nam.hv@unitel.com.la", "8562055600011", 30, 2),
            user("souphaphone.d", "Souphaphone Douangchanh", "souphaphone.d@unitel.com.la", "8562055610012", 25, 9),
            user("le.huong", "Lê Thị Hương", "huong.lt@unitel.com.la", "8562055620013", 40, 1),
            user("pham.duc", "Phạm Minh Đức", "duc.pm@unitel.com.la", "8562055630014", 35, 5),
            user("somchai.k", "Somchai Keovilay", "somchai.k@unitel.com.la", "8562055640015", 20, 1),
            user("viengxay.l", "Viengxay Luangrath", "viengxay.l@unitel.com.la", "8562055650016", 14, 3),
            user("do.trang", "Đỗ Thu Trang", "trang.dt@unitel.com.la", "8562055660017", 70, 13),
            user("khamla.s", "Khamla Souvannavong", "khamla.s@unitel.com.la", "8562055670018", 4, 0),
            new UserSpec("ngo.linh", "Ngô Thùy Linh", "linh.nt@unitel.com.la", "8562055680019", SystemRole.USER,
                    UserStatus.LOCKED, 45, 30),
            user("thongsavanh.b", "Thongsavanh Bounphengsy", "thongsavanh.b@unitel.com.la", "8562055690020", 200, 95));

    static final List<WsSpec> WORKSPACES = List.of(
            new WsSpec("BSD", "BSD", "Workspace khối kinh doanh dịch vụ — chiến dịch truyền thông & ưu đãi thuê bao di động",
                    WorkspaceStatus.ACTIVE, "Souksavanh Phommachanh", "bsd@unitel.com.la", "8562055510002", 110),
            new WsSpec("BCD", "BCD", "Workspace mở rộng cho phòng ban BCD", WorkspaceStatus.ACTIVE, null, null, null, 30),
            new WsSpec("MKT", "Marketing & Truyền thông", "Chiến dịch thương hiệu, sự kiện và truyền thông số",
                    WorkspaceStatus.ACTIVE, "Chanthala Phimmasone", "mkt@unitel.com.la", "8562055570008", 75),
            new WsSpec("VAS", "Dịch vụ giá trị gia tăng", "Tạm khoá — chờ chuyển dữ liệu sang hệ thống mới",
                    WorkspaceStatus.LOCKED, "Thongsavanh Bounphengsy", "vas@unitel.com.la", "8562055690020", 200));

    /** BCD cố ý KHÔNG có OWNER (ca kiểm thử của DevDataSeeder). VAS bị khoá → non-admin nhận 403. */
    static final List<MemberSpec> MEMBERS = List.of(
            new MemberSpec("BSD", "vilay.k", MemberRole.MEMBER, 60, null),
            new MemberSpec("BSD", "tran.minh", MemberRole.MEMBER, 80, null),
            new MemberSpec("BSD", "le.huong", MemberRole.MEMBER, 40, null),
            new MemberSpec("BSD", "pham.duc", MemberRole.MEMBER, 35, null),
            new MemberSpec("BSD", "somchai.k", MemberRole.MEMBER, 20, null),
            new MemberSpec("BSD", "viengxay.l", MemberRole.MEMBER, 14, null),
            new MemberSpec("BSD", "do.trang", MemberRole.MEMBER, 70, 12),
            new MemberSpec("BCD", "noy.s", MemberRole.ADMIN, 5, null),
            new MemberSpec("BCD", "khamla.s", MemberRole.MEMBER, 4, null),
            new MemberSpec("MKT", "chanthala.p", MemberRole.OWNER, 75, null),
            new MemberSpec("MKT", "bounmy.v", MemberRole.ADMIN, 70, null),
            new MemberSpec("MKT", "phouvong.s", MemberRole.MEMBER, 50, null),
            new MemberSpec("MKT", "ngo.linh", MemberRole.MEMBER, 45, null),
            new MemberSpec("MKT", "hoang.nam", MemberRole.MEMBER, 30, null),
            new MemberSpec("MKT", "souphaphone.d", MemberRole.MEMBER, 25, null),
            new MemberSpec("MKT", "admin_bsd", MemberRole.MEMBER, 20, null),
            new MemberSpec("VAS", "thongsavanh.b", MemberRole.OWNER, 200, null),
            new MemberSpec("VAS", "owner_bsd", MemberRole.MEMBER, 150, null));

    /** Đơn giá gốc (LAK) theo FE mock. */
    static final Map<ActionType, Integer> BASE_PRICE = Map.ofEntries(
            Map.entry(ActionType.TOP_UP, 0), Map.entry(ActionType.ADD_MONEY, 0), Map.entry(ActionType.ADD_PROMOTION, 0),
            Map.entry(ActionType.ADD_DATA, 120), Map.entry(ActionType.ADD_SMS, 40),
            Map.entry(ActionType.ADD_CALL_MINUTES, 90), Map.entry(ActionType.ADD_PACKAGE, 250),
            Map.entry(ActionType.REGISTER_CRBT, 150), Map.entry(ActionType.REGISTER_LAOTV, 200),
            Map.entry(ActionType.NOTIFICATION_SMS, 7), Map.entry(ActionType.NOTIFICATION_EMAIL, 2),
            Map.entry(ActionType.NOTIFICATION_WHATSAPP, 35), Map.entry(ActionType.NOTIFICATION_USSD, 5));

    private final AppUserRepository userRepo;
    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository memberRepo;
    private final PriceQuoteRepository priceQuoteRepo;
    private final PriceQuoteService priceQuoteService;
    private final AuditWriter auditWriter;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PlatformTransactionManager txManager;

    private ZonedDateTime now;
    private final Random rnd = new Random(20261115L);
    private final Map<String, AppUser> users = new HashMap<>();
    private final Map<String, Workspace> workspaces = new HashMap<>();
    private final Map<String, Long> memberIds = new HashMap<>();
    private final Map<String, Long> quoteIds = new HashMap<>();

    @Order(10) // sau DevDataSeeder (@Order(0)): cần 4 tài khoản + BSD/BCD gốc
    @EventListener(DatabaseReadyEvent.class)
    public void onDatabaseReady() {
        try {
            now = ZonedDateTime.now(VTE);
            new TransactionTemplate(txManager).executeWithoutResult(status -> seed());
        } catch (Exception e) {
            log.warn("[A++] Nạp mock data dev bị bỏ qua: {}", e.getMessage(), e);
        }
    }

    private void seed() {
        int newUsers = seedUsers();
        int newWorkspaces = seedWorkspaces();
        int newMembers = seedMembers();
        int newQuotes = seedPriceQuotes();
        int newAudits = seedAuditLogs();
        log.info("[MOCK] Dev mock data: +{} user, +{} workspace, +{} thành viên, +{} bảng giá, +{} dòng nhật ký",
                newUsers, newWorkspaces, newMembers, newQuotes, newAudits);
    }

    // ───────────── Người dùng ─────────────

    private int seedUsers() {
        for (UserSpec s : BASE_USERS) {
            userRepo.findByUsername(s.username()).ifPresent(u -> {
                if (u.getPhone() == null && SEED_ACTOR.equals(u.getCreatedBy())) {
                    applyProfile(u, s);
                }
                users.put(s.username(), u);
            });
        }
        int created = 0;
        for (UserSpec s : EXTRA_USERS) {
            AppUser u = userRepo.findByUsername(s.username()).orElse(null);
            if (u == null) {
                u = new AppUser();
                u.setUsername(s.username());
                u.setPasswordHash(encoder.encode(MOCK_PASSWORD));
                u.setSystemRole(s.role());
                u.setCreatedBy(SEED_ACTOR);
                applyProfile(u, s);
                u = userRepo.save(u);
                created++;
            }
            users.put(s.username(), u);
        }
        return created;
    }

    private void applyProfile(AppUser u, UserSpec s) {
        u.setFullName(s.fullName());
        u.setEmail(s.email());
        u.setPhone(s.phone());
        u.setStatus(s.status());
        u.setCreatedAt(at(s.createdDaysAgo(), 9));
        u.setLastLoginAt(s.lastLoginDaysAgo() == null ? null : at(s.lastLoginDaysAgo(), 8 + rnd.nextInt(9)));
    }

    // ───────────── Workspace + thành viên ─────────────

    private int seedWorkspaces() {
        int created = 0;
        for (WsSpec s : WORKSPACES) {
            Workspace w = workspaceRepo.findByCode(s.code()).orElse(null);
            if (w == null) {
                w = new Workspace();
                w.setCode(s.code());
                w.setCreatedBy(SEED_ACTOR);
                applyWorkspace(w, s);
                w = workspaceRepo.save(w);
                created++;
            } else if (w.getDescription() == null && SEED_ACTOR.equals(w.getCreatedBy())) {
                applyWorkspace(w, s);
            }
            workspaces.put(s.code(), w);
        }
        return created;
    }

    private void applyWorkspace(Workspace w, WsSpec s) {
        w.setName(s.name());
        w.setDescription(s.description());
        w.setStatus(s.status());
        w.setContactName(s.contactName());
        w.setContactEmail(s.contactEmail());
        w.setContactPhone(s.contactPhone());
        w.setCreatedAt(at(s.createdDaysAgo(), 10));
    }

    private int seedMembers() {
        int created = 0;
        for (MemberSpec s : MEMBERS) {
            Workspace ws = workspaces.get(s.code());
            AppUser u = users.get(s.username());
            if (ws == null || u == null) {
                continue;
            }
            WorkspaceMember m = memberRepo.findByWorkspaceIdAndUser_Id(ws.getId(), u.getId()).orElse(null);
            if (m == null) {
                m = new WorkspaceMember();
                m.setWorkspaceId(ws.getId());
                m.setUser(u);
                m.setRole(s.role());
                m.setJoinedAt(at(s.joinedDaysAgo(), 10));
                m.setAddedBy(SEED_ACTOR);
                if (s.revokedDaysAgo() == null) {
                    m.setStatus(MemberStatus.ACTIVE);
                } else {
                    m.setStatus(MemberStatus.REVOKED);
                    m.setRevokedAt(at(s.revokedDaysAgo(), 15));
                    m.setRevokedBy("owner_bsd");
                }
                m = memberRepo.save(m);
                created++;
            }
            memberIds.put(s.code() + "/" + s.username(), m.getId());
        }
        // thành viên gốc của DevDataSeeder — cần id cho nhật ký
        for (String username : List.of("owner_bsd", "admin_bsd", "member_bsd")) {
            AppUser u = users.get(username);
            Workspace bsd = workspaces.get("BSD");
            if (u != null && bsd != null) {
                memberRepo.findByWorkspaceIdAndUser_Id(bsd.getId(), u.getId())
                        .ifPresent(m -> memberIds.put("BSD/" + username, m.getId()));
            }
        }
        return created;
    }

    // ───────────── Bảng giá: Q3 ENDED → Q4 LOCKED → Q1/2027 DRAFT ─────────────

    private int seedPriceQuotes() {
        int created = 0;
        boolean hasLocked = priceQuoteRepo.findFirstByStatusOrderByLockedAtDesc(PriceQuoteStatus.LOCKED).isPresent();
        if (!hasLocked && !priceQuoteRepo.existsByName(Q3) && !priceQuoteRepo.existsByName(Q4)) {
            PriceQuote q3 = createQuote(Q3, null, 0.95, 135);
            q3.setStatus(PriceQuoteStatus.ENDED);
            q3.setLockedAt(at(130, 10));
            q3.setLockedBy("superadmin");
            q3.setEndedAt(at(45, 10));
            q3.setEndedBy("superadmin");
            q3.setUpdatedAt(at(45, 10));
            q3.setUpdatedBy("superadmin");
            PriceQuote q4 = createQuote(Q4, null, 1.0, 50);
            q4.setStatus(PriceQuoteStatus.LOCKED);
            q4.setLockedAt(at(45, 10));
            q4.setLockedBy("superadmin");
            q4.setUpdatedAt(at(45, 10));
            q4.setUpdatedBy("superadmin");
            created += 2;
        }
        if (!priceQuoteRepo.existsByName(Q1_DRAFT)) {
            PriceQuote q1 = createQuote(Q1_DRAFT, "Điều chỉnh đơn giá WhatsApp theo hợp đồng Meta mới", 1.1, 3);
            q1.setUpdatedAt(at(1, 14));
            q1.setUpdatedBy("superadmin");
            created++;
        }
        return created;
    }

    private PriceQuote createQuote(String name, String note, double factor, int createdDaysAgo) {
        List<PriceItemCommand> items = Arrays.stream(ActionType.values()).map(t -> {
            boolean timed = t == ActionType.TOP_UP || t.name().startsWith("ADD_");
            BigDecimal unit = BigDecimal.valueOf(BASE_PRICE.getOrDefault(t, 0) * factor).setScale(0, RoundingMode.HALF_UP);
            return new PriceItemCommand(t, unit, timed ? BigDecimal.valueOf(2) : BigDecimal.ZERO, timed);
        }).toList();
        PriceQuote q = priceQuoteService.create(name, note, items, "superadmin");
        q.setCreatedAt(at(createdDaysAgo, 9));
        quoteIds.put(name, q.getId());
        return q;
    }

    // ───────────── Nhật ký (lùi ngày, đúng định dạng summary mà @Auditable của BE ghi) ─────────────

    private int seedAuditLogs() {
        Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM LADS_AUDIT_LOG WHERE REQUEST_ID = ?", Integer.class,
                AUDIT_MARKER);
        if (done != null && done > 0) {
            return 0;
        }
        List<AuditEvent> events = new ArrayList<>();
        Long bsd = wsId("BSD");
        Long bcd = wsId("BCD");
        Long mkt = wsId("MKT");
        Long vas = wsId("VAS");

        add(events, "superadmin", "CREATE", "WORKSPACE", str(vas), vas, "Tạo workspace VAS (OWNER: thongsavanh.b)", 200);
        add(events, "superadmin", "LOCK", "PRICE_QUOTE", quote(Q3), null, "Khoá (áp dụng) bảng giá " + Q3, 130);
        add(events, "superadmin", "CREATE", "WORKSPACE", str(bsd), bsd, "Tạo workspace BSD (OWNER: owner_bsd)", 110);
        add(events, "owner_bsd", "CREATE", "MEMBER", member("BSD/admin_bsd"), bsd, "Thêm thành viên admin_bsd với vai trò MEMBER", 100);
        add(events, "owner_bsd", "CREATE", "MEMBER", member("BSD/member_bsd"), bsd, "Thêm thành viên member_bsd với vai trò MEMBER", 90);
        add(events, "superadmin", "LOCK", "WORKSPACE", str(vas), vas, "Khoá workspace VAS", 88);
        add(events, "owner_bsd", "CREATE", "MEMBER", member("BSD/tran.minh"), bsd, "Thêm thành viên tran.minh với vai trò MEMBER", 80);
        add(events, "superadmin", "CREATE", "WORKSPACE", str(mkt), mkt, "Tạo workspace MKT (OWNER: chanthala.p)", 75);
        add(events, "owner_bsd", "CREATE", "MEMBER", member("BSD/do.trang"), bsd, "Thêm thành viên do.trang với vai trò MEMBER", 70);
        add(events, "chanthala.p", "CREATE", "MEMBER", member("MKT/bounmy.v"), mkt, "Thêm thành viên bounmy.v với vai trò MEMBER", 70);
        add(events, "owner_bsd", "CREATE", "MEMBER", member("BSD/vilay.k"), bsd, "Thêm thành viên vilay.k với vai trò MEMBER", 60);
        add(events, "superadmin", "CREATE", "PRICE_QUOTE", quote(Q4), null, "Tạo bảng giá " + Q4, 50);
        add(events, "superadmin", "LOCK", "PRICE_QUOTE", quote(Q4), null, "Khoá (áp dụng) bảng giá " + Q4, 45);
        add(events, "superadmin", "LOCK", "USER", userId("ngo.linh"), null, "Khoá tài khoản ngo.linh", 42);
        add(events, "superadmin", "RESET_PASSWORD", "USER", userId("tran.minh"), null,
                "Đặt lại mật khẩu cho user id=" + userId("tran.minh"), 40);
        add(events, "superadmin", "LOCK", "USER", userId("tran.minh"), null, "Khoá tài khoản tran.minh", 38);
        add(events, "owner_bsd", "APPROVE", "CAMPAIGN", "5", bsd, "Duyệt chiến dịch \"Phát triển thuê bao Data 4G – Vòng 1\"", 32);
        add(events, "superadmin", "CREATE", "WORKSPACE", str(bcd), bcd, "Tạo workspace BCD (OWNER: null)", 30);
        add(events, "chanthala.p", "UPDATE", "WORKSPACE", str(mkt), mkt, "Cập nhật thông tin workspace MKT", 26);
        add(events, "admin_bsd", "CREATE", "SEGMENT", "1", bsd, "Tạo tệp KH \"Thuê bao 4G chưa dùng data – T11/2026\"", 21);
        add(events, "owner_bsd", "CHANGE_ROLE", "MEMBER", member("BSD/admin_bsd"), bsd, "Đổi vai trò admin_bsd thành ADMIN", 15);
        add(events, "owner_bsd", "DELETE", "MEMBER", member("BSD/do.trang"), bsd,
                "Gỡ thành viên (memberId=" + member("BSD/do.trang") + ")", 12);
        add(events, "owner_bsd", "UPDATE", "WORKSPACE", str(bsd), bsd, "Cập nhật thông tin workspace BSD", 10);
        add(events, "superadmin", "UPDATE", "ROLE_PERMISSION", "MEMBER", null, "Cập nhật quyền vai trò MEMBER: [WORKSPACE_VIEW, "
                + "CAMPAIGN_VIEW, CAMPAIGN_CREATE, CAMPAIGN_UPDATE, CAMPAIGN_SUBMIT, SEGMENT_MANAGE, REPORT_VIEW]", 9);
        add(events, "admin_bsd", "IMPORT", "SEGMENT", "4", bsd, "Import file vip_vientiane_2026-11.xlsx (4.812 dòng)", 8);
        add(events, "owner_bsd", "EXPORT", "AUDIT_LOG", null, bsd, "Xuất Excel nhật ký hệ thống", 7);
        add(events, "superadmin", "CREATE", "USER", userId("noy.s"), null, "Tạo tài khoản noy.s (USER)", 5);
        add(events, "superadmin", "CREATE", "MEMBER", member("BCD/noy.s"), bcd, "Thêm thành viên noy.s với vai trò ADMIN", 5);
        add(events, "noy.s", "CREATE", "MEMBER", member("BCD/khamla.s"), bcd, "Thêm thành viên khamla.s với vai trò MEMBER", 4);
        add(events, "superadmin", "CREATE", "PRICE_QUOTE", quote(Q1_DRAFT), null, "Tạo bảng giá " + Q1_DRAFT, 3);
        add(events, "owner_bsd", "REJECT", "CAMPAIGN", "10", bsd, "Từ chối chiến dịch \"Khuyến mãi nạp thẻ cuối tuần\"", 2);
        add(events, "superadmin", "UPDATE", "PRICE_QUOTE", quote(Q1_DRAFT), null, "Cập nhật bảng giá " + Q1_DRAFT, 1);
        add(events, "admin_bsd", "SUBMIT", "CAMPAIGN", "7", bsd, "Gửi duyệt \"Ưu đãi LaoTV cuối năm\"", 1);
        add(events, "member_bsd", "SUBMIT", "CAMPAIGN", "8", bsd, "Gửi duyệt \"Đăng ký Super App tặng 1GB\"", 1);

        // Đăng nhập rải rác 30 ngày gần nhất (deterministic) — LOGIN không gắn workspace
        List<String> active = users.values().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getLastLoginAt() != null)
                .map(AppUser::getUsername).sorted().toList();
        for (int d = 29; d >= 0 && !active.isEmpty(); d--) {
            int n = 1 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                String who = active.get(rnd.nextInt(active.size()));
                add(events, who, "LOGIN", "USER", userId(who), null, "Đăng nhập bằng mật khẩu", d);
            }
        }

        events.stream().sorted(Comparator.comparing(AuditEvent::createdAt)).forEach(auditWriter::write);
        return events.size();
    }

    private void add(List<AuditEvent> events, String actor, String action, String entity, String entityId,
                     Long workspaceId, String summary, int daysAgo) {
        AppUser u = users.get(actor);
        events.add(new AuditEvent(actor, u == null ? null : u.getId(), action, entity, entityId, workspaceId, summary,
                null, "10.120.30." + (10 + rnd.nextInt(240)), UA, AUDIT_MARKER, at(daysAgo, 8 + rnd.nextInt(10))));
    }

    // ───────────── tiện ích ─────────────

    /** Thời điểm {@code daysAgo} ngày trước lúc {@code hour} giờ (giờ Lào); không vượt quá hiện tại. */
    private Instant at(int daysAgo, int hour) {
        ZonedDateTime t = now.minusDays(daysAgo).withHour(hour).withMinute(rnd.nextInt(60)).withSecond(rnd.nextInt(60))
                .withNano(0);
        return (t.isAfter(now) ? now.minusMinutes(5L + rnd.nextInt(50)) : t).toInstant();
    }

    private Long wsId(String code) {
        Workspace w = workspaces.get(code);
        return w == null ? null : w.getId();
    }

    private String userId(String username) {
        AppUser u = users.get(username);
        return u == null ? null : String.valueOf(u.getId());
    }

    private String member(String key) {
        return str(memberIds.get(key));
    }

    private String quote(String name) {
        return str(quoteIds.get(name));
    }

    private static String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private static UserSpec user(String username, String fullName, String email, String phone, int createdDaysAgo,
                                 Integer lastLoginDaysAgo) {
        return user(username, fullName, email, phone, SystemRole.USER, createdDaysAgo, lastLoginDaysAgo);
    }

    private static UserSpec user(String username, String fullName, String email, String phone, SystemRole role,
                                 int createdDaysAgo, Integer lastLoginDaysAgo) {
        return new UserSpec(username, fullName, email, phone, role, UserStatus.ACTIVE, createdDaysAgo, lastLoginDaysAgo);
    }
}
