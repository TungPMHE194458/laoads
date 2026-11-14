package com.unitel.laoads.domain.user;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.auth.RefreshTokenRepository;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** Quản trị tài khoản (SUPER_ADMIN) — contract §6. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final AppUserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<AppUser> search(String query, UserStatus status, SystemRole systemRole, Pageable pageable) {
        return userRepo.search(Texts.likePattern(query), status, systemRole, pageable);
    }

    @Transactional(readOnly = true)
    public AppUser get(long id) {
        return userRepo.findById(id).orElseThrow(() -> LaoadsException.notFound("error.notFound.user",
                "Không tìm thấy người dùng id=" + id, id));
    }

    /** Trùng username (không phân biệt hoa thường) → 409 USERNAME_DUPLICATE. */
    @Transactional
    public AppUser create(String username, String fullName, String email, String phone, String rawPassword,
                          SystemRole systemRole, AuthUser actor) {
        String normalized = Texts.normalizeUsername(username);
        if (userRepo.existsByUsername(normalized)) {
            throw new LaoadsException(ErrorCode.USERNAME_DUPLICATE,
                    "Tên đăng nhập " + normalized + " đã tồn tại", normalized);
        }
        AppUser u = new AppUser();
        u.setUsername(normalized);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setFullName(Texts.trimToNull(fullName));
        u.setEmail(Texts.trimToNull(email));
        u.setPhone(Texts.trimToNull(phone));
        u.setSystemRole(systemRole);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(Instant.now());
        u.setCreatedBy(actor.username());
        AppUser saved = userRepo.save(u);
        log.info("[USER] {} tạo user {} ({})", actor.username(), normalized, systemRole);
        return saved;
    }

    /** Không cho tự đổi system role của chính mình (tránh SUPER_ADMIN tự hạ quyền) → 400 CANNOT_CHANGE_SELF. */
    @Transactional
    public AppUser update(long id, String fullName, String email, String phone, SystemRole systemRole,
                          AuthUser actor) {
        AppUser u = get(id);
        if (Objects.equals(u.getId(), actor.id()) && u.getSystemRole() != systemRole) {
            throw LaoadsException.withKey(ErrorCode.CANNOT_CHANGE_SELF, "error.CANNOT_CHANGE_SELF.systemRole",
                    "Không thể tự thay đổi vai trò hệ thống của chính mình");
        }
        u.setFullName(Texts.trimToNull(fullName));
        u.setEmail(Texts.trimToNull(email));
        u.setPhone(Texts.trimToNull(phone));
        u.setSystemRole(systemRole);
        u.setUpdatedAt(Instant.now());
        u.setUpdatedBy(actor.username());
        return u;
    }

    /** Không tự khoá mình → 400 CANNOT_LOCK_SELF. Khoá → thu hồi mọi refresh token. */
    @Transactional
    public AppUser changeStatus(long id, UserStatus status, AuthUser actor) {
        AppUser u = get(id);
        if (status == UserStatus.LOCKED && Objects.equals(u.getId(), actor.id())) {
            throw new LaoadsException(ErrorCode.CANNOT_LOCK_SELF, "Không thể tự khoá tài khoản của chính mình");
        }
        Instant now = Instant.now();
        u.setStatus(status);
        u.setUpdatedAt(now);
        u.setUpdatedBy(actor.username());
        if (status == UserStatus.LOCKED) {
            refreshRepo.revokeAllOfUser(u.getId(), now);
        }
        log.info("[USER] {} đổi trạng thái {} → {}", actor.username(), u.getUsername(), status);
        return u;
    }

    /** Admin đặt mật khẩu mới → thu hồi mọi refresh token của user. */
    @Transactional
    public AppUser resetPassword(long id, String newRaw, AuthUser actor) {
        AppUser u = get(id);
        Instant now = Instant.now();
        u.setPasswordHash(passwordEncoder.encode(newRaw));
        u.setUpdatedAt(now);
        u.setUpdatedBy(actor.username());
        refreshRepo.revokeAllOfUser(u.getId(), now);
        log.warn("[USER] {} đặt lại mật khẩu cho {}", actor.username(), u.getUsername());
        return u;
    }
}
