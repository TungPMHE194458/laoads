package com.unitel.laoads.web.bootstrap;

import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserStatus;
import com.unitel.laoads.infra.bootstrap.DatabaseReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Tạo Super Admin đầu tiên cho uat/prod từ biến môi trường {@code LAOADS_BOOTSTRAP_ADMIN_USERNAME} /
 * {@code _PASSWORD} (/ {@code _FULL_NAME}). Chỉ tạo khi hệ thống CHƯA có SUPER_ADMIN nào — không bao giờ ghi đè
 * hay đổi mật khẩu tài khoản có sẵn. Nếu dùng VSA, đặt username trùng tài khoản VSA.
 *
 * <p>Thay cho việc seed tài khoản demo ở uat (DevDataSeeder giờ chỉ chạy ở dev). Chạy sau khi Flyway migrate xong.
 */
@Component
@Profile({"uat", "prod"})
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap {

    static final int MIN_PASSWORD_LENGTH = 8;
    static final String ACTOR = "bootstrap";

    private final AppUserRepository userRepo;
    private final PasswordEncoder encoder;
    private final PlatformTransactionManager txManager;
    private final Environment env;

    enum Result { CREATED, ALREADY_HAS_SUPER_ADMIN, NOT_CONFIGURED, INVALID_CONFIG }

    @Order(0)
    @EventListener(DatabaseReadyEvent.class)
    public void onDatabaseReady() {
        try {
            Result r = new TransactionTemplate(txManager).execute(status -> ensureSuperAdmin(
                    env.getProperty("laoads.bootstrap.admin-username"),
                    env.getProperty("laoads.bootstrap.admin-password"),
                    env.getProperty("laoads.bootstrap.admin-full-name")));
            log.info("[BOOTSTRAP] Super Admin: {}", r);
        } catch (Exception e) {
            log.warn("[A++] Bootstrap Super Admin bị bỏ qua: {}", e.getMessage());
        }
    }

    Result ensureSuperAdmin(String rawUsername, String password, String fullName) {
        if (userRepo.existsBySystemRole(SystemRole.SUPER_ADMIN)) {
            return Result.ALREADY_HAS_SUPER_ADMIN;
        }
        String username = Texts.normalizeUsername(rawUsername);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("[BOOTSTRAP] Hệ thống chưa có SUPER_ADMIN — đặt LAOADS_BOOTSTRAP_ADMIN_USERNAME và "
                    + "LAOADS_BOOTSTRAP_ADMIN_PASSWORD rồi khởi động lại để tạo tài khoản quản trị đầu tiên.");
            return Result.NOT_CONFIGURED;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("[BOOTSTRAP] LAOADS_BOOTSTRAP_ADMIN_PASSWORD phải có ít nhất {} ký tự — không tạo tài khoản.",
                    MIN_PASSWORD_LENGTH);
            return Result.INVALID_CONFIG;
        }
        if (userRepo.existsByUsername(username)) {
            log.error("[BOOTSTRAP] Username '{}' đã tồn tại nhưng không phải SUPER_ADMIN — không tự nâng quyền. "
                    + "Chọn username khác hoặc nâng quyền thủ công.", username);
            return Result.INVALID_CONFIG;
        }
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(Texts.trimToNull(fullName));
        u.setSystemRole(SystemRole.SUPER_ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(Instant.now());
        u.setCreatedBy(ACTOR);
        userRepo.save(u);
        log.info("[BOOTSTRAP] Đã tạo Super Admin '{}' — nên đổi mật khẩu sau lần đăng nhập đầu và bỏ biến "
                + "LAOADS_BOOTSTRAP_ADMIN_PASSWORD khỏi môi trường.", username);
        return Result.CREATED;
    }
}
