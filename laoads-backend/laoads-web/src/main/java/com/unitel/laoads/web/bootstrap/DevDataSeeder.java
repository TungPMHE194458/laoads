package com.unitel.laoads.web.bootstrap;

import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.pricing.PriceItemCommand;
import com.unitel.laoads.domain.pricing.PriceQuoteRepository;
import com.unitel.laoads.domain.pricing.PriceQuoteService;
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
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Seed dữ liệu demo — CHỈ profile dev/uat, idempotent (chỉ tạo khi chưa có).
 *
 * <p>Khác BOC DemoUserSeeder (CommandLineRunner lúc startup — chạy trước khi Flyway migrate nên lần đầu
 * luôn hỏng): seeder này lắng nghe {@link DatabaseReadyEvent} → chạy đúng sau khi migrate xong, kể cả khi DB
 * lên muộn. Vẫn bọc try/catch (A++) và dùng TransactionTemplate thay @Transactional tự gọi (Sonar S2229/S6809).
 */
@Component
@Profile({"dev", "uat"})
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder {

    static final String SAMPLE_QUOTE_NAME = "Bảng giá mẫu (seed)";
    private static final String SEED_ACTOR = "seed";

    private final AppUserRepository userRepo;
    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository memberRepo;
    private final PriceQuoteRepository priceQuoteRepo;
    private final PriceQuoteService priceQuoteService;
    private final PasswordEncoder encoder;
    private final PlatformTransactionManager txManager;

    @EventListener(DatabaseReadyEvent.class)
    public void onDatabaseReady() {
        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> doSeed());
        } catch (Exception e) {
            log.warn("[A++] Seed dữ liệu demo bị bỏ qua: {}", e.getMessage());
        }
    }

    private void doSeed() {
        seedUser("superadmin", "superadmin", "Super Admin", "superadmin@laoads.local", SystemRole.SUPER_ADMIN);
        AppUser owner = seedUser("owner_bsd", "owner", "Owner BSD", "owner.bsd@laoads.local", SystemRole.USER);
        AppUser admin = seedUser("admin_bsd", "admin", "Admin BSD", "admin.bsd@laoads.local", SystemRole.USER);
        AppUser member = seedUser("member_bsd", "member", "Member BSD", "member.bsd@laoads.local", SystemRole.USER);

        Workspace bsd = seedWorkspace("BSD", "BSD");
        Workspace bcd = seedWorkspace("BCD", "BCD");

        seedMember(bsd, owner, MemberRole.OWNER);
        seedMember(bsd, admin, MemberRole.ADMIN);
        seedMember(bsd, member, MemberRole.MEMBER);
        seedMember(bcd, member, MemberRole.MEMBER);

        if (!priceQuoteRepo.existsByName(SAMPLE_QUOTE_NAME)) {
            List<PriceItemCommand> items = Arrays.stream(ActionType.values())
                    .map(t -> new PriceItemCommand(t, BigDecimal.valueOf(1000), BigDecimal.ZERO, false))
                    .toList();
            priceQuoteService.create(SAMPLE_QUOTE_NAME, "Dữ liệu demo — đủ 13 loại hành động", items, SEED_ACTOR);
            log.info("[SEED] Tạo bảng giá DRAFT mẫu ({} dòng)", items.size());
        }
    }

    private AppUser seedUser(String username, String rawPassword, String fullName, String email, SystemRole role) {
        return userRepo.findByUsername(username).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setUsername(username);
            u.setPasswordHash(encoder.encode(rawPassword));
            u.setFullName(fullName);
            u.setEmail(email);
            u.setSystemRole(role);
            u.setStatus(UserStatus.ACTIVE);
            u.setCreatedAt(Instant.now());
            u.setCreatedBy(SEED_ACTOR);
            log.info("[SEED] Tạo user {} ({})", username, role);
            return userRepo.save(u);
        });
    }

    private Workspace seedWorkspace(String code, String name) {
        return workspaceRepo.findByCode(code).orElseGet(() -> {
            Workspace w = new Workspace();
            w.setCode(code);
            w.setName(name);
            w.setStatus(WorkspaceStatus.ACTIVE);
            w.setCreatedAt(Instant.now());
            w.setCreatedBy(SEED_ACTOR);
            log.info("[SEED] Tạo workspace {}", code);
            return workspaceRepo.save(w);
        });
    }

    private void seedMember(Workspace ws, AppUser user, MemberRole role) {
        if (memberRepo.findByWorkspaceIdAndUser_Id(ws.getId(), user.getId()).isPresent()) {
            return;
        }
        WorkspaceMember m = new WorkspaceMember();
        m.setWorkspaceId(ws.getId());
        m.setUser(user);
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE);
        m.setJoinedAt(Instant.now());
        m.setAddedBy(SEED_ACTOR);
        memberRepo.save(m);
        log.info("[SEED] {} → {} ({})", user.getUsername(), ws.getCode(), role);
    }
}
