package com.unitel.laoads.domain.auth;

import com.unitel.laoads.domain.auth.vsa.VsaClient;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Đăng nhập (mật khẩu / VSA), xoay vòng refresh token, đăng xuất, đổi mật khẩu.
 * Refresh token lưu dạng SHA-256 trong LADS_REFRESH_TOKEN; access token JWT do {@link TokenIssuer} ký.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AppUserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final VsaClient vsaClient;
    private final PlatformTransactionManager txManager;

    /** Sai username/mật khẩu → 401 UNAUTHORIZED; đúng mật khẩu nhưng tài khoản LOCKED → 403 USER_LOCKED. */
    @Transactional
    public AuthSession login(String username, String rawPassword, ClientInfo client) {
        AppUser user = userRepo.findByUsername(Texts.normalizeUsername(username))
                .orElseThrow(AuthService::badCredentials);
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("[AUTH] Sai mật khẩu cho user {}", user.getUsername());
            throw badCredentials();
        }
        ensureActive(user);
        user.setLastLoginAt(Instant.now());
        return openSession(user, AuthMethod.PASSWORD, client).session();
    }

    /**
     * Đăng nhập VSA (CAS 2.0): validate ticket → username VSA → phải map tới user local ACTIVE.
     * Tắt VSA → 400 VSA_DISABLED; user chưa được cấp → 403 VSA_USER_NOT_PROVISIONED.
     *
     * <p>KHÔNG @Transactional ở method: kiểm tra cấu hình + gọi CAS (HTTP) chạy ngoài transaction để
     * (1) VSA_DISABLED trả đúng mã kể cả khi DB down, (2) không giữ connection DB trong lúc chờ CAS.
     * Phần ghi DB dùng TransactionTemplate (tránh self-invocation @Transactional — Sonar S6809).
     */
    public AuthSession vsaLogin(String ticket, String service, ClientInfo client) {
        if (!vsaClient.isEnabled()) {
            throw new LaoadsException(ErrorCode.VSA_DISABLED, "Đăng nhập VSA chưa được bật/cấu hình");
        }
        String effectiveService = Texts.trimToNull(service) != null ? service.trim() : vsaClient.defaultServiceUrl();
        String configured = Texts.trimToNull(vsaClient.defaultServiceUrl());
        if (configured != null && (effectiveService == null || !effectiveService.startsWith(configured))) {
            throw LaoadsException.withKey(ErrorCode.INVALID_PARAM, "error.INVALID_PARAM.vsaService",
                    "Tham số service không khớp cấu hình VSA");
        }
        String vsaUsername = vsaClient.validateTicket(ticket, effectiveService);
        return new TransactionTemplate(txManager).execute(status -> {
            AppUser user = userRepo.findByUsername(Texts.normalizeUsername(vsaUsername))
                    .filter(AppUser::isActive)
                    .orElseThrow(() -> new LaoadsException(ErrorCode.VSA_USER_NOT_PROVISIONED,
                            "Tài khoản VSA " + vsaUsername + " chưa được cấp quyền truy cập LaoAds", vsaUsername));
            user.setLastLoginAt(Instant.now());
            log.info("[AUTH] VSA login OK: {}", user.getUsername());
            return openSession(user, AuthMethod.VSA, client).session();
        });
    }

    /** Xoay vòng: thu hồi refresh token cũ, phát cặp token mới. Token sai/hết hạn/đã thu hồi → 401 UNAUTHORIZED. */
    @Transactional
    public AuthSession refresh(String rawRefreshToken, ClientInfo client) {
        Instant now = Instant.now();
        RefreshToken current = findToken(rawRefreshToken)
                .filter(t -> t.isUsable(now))
                .orElseThrow(AuthService::refreshInvalid);
        AppUser user = userRepo.findById(current.getUserId()).orElseThrow(AuthService::refreshInvalid);
        if (!user.isActive()) {
            throw lockedError();
        }
        OpenedSession next = openSession(user, current.getAuthMethod(), client);
        current.setRevokedAt(now);
        current.setReplacedBy(next.refreshTokenId());
        return next.session();
    }

    /** Thu hồi refresh token. Idempotent: token không tồn tại / đã thu hồi → vẫn OK. */
    @Transactional
    public void logout(String rawRefreshToken) {
        findToken(rawRefreshToken).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
            }
        });
    }

    /**
     * Đổi mật khẩu. Sai mật khẩu cũ → 400 WRONG_PASSWORD (KHÔNG 401 — tránh FE tự logout).
     * Thành công → thu hồi mọi phiên khác, giữ phiên hiện tại ({@code sessionId}).
     */
    @Transactional
    public void changePassword(AuthUser actor, String oldRaw, String newRaw) {
        AppUser user = userRepo.findById(actor.id())
                .orElseThrow(() -> new LaoadsException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        if (oldRaw == null || !passwordEncoder.matches(oldRaw, user.getPasswordHash())) {
            log.warn("[AUTH] Change-password: sai mật khẩu cũ cho user {}", user.getUsername());
            throw new LaoadsException(ErrorCode.WRONG_PASSWORD, "Mật khẩu hiện tại không đúng");
        }
        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(newRaw));
        user.setUpdatedAt(now);
        user.setUpdatedBy(user.getUsername());
        if (actor.sessionId() != null) {
            refreshRepo.revokeOthersOfUser(user.getId(), actor.sessionId(), now);
        } else {
            refreshRepo.revokeAllOfUser(user.getId(), now);
        }
        log.info("[AUTH] User {} đã đổi mật khẩu", user.getUsername());
    }

    @Transactional(readOnly = true)
    public AppUser currentUser(AuthUser actor) {
        return userRepo.findById(actor.id())
                .orElseThrow(() -> new LaoadsException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
    }

    private record OpenedSession(AuthSession session, Long refreshTokenId) {
    }

    private OpenedSession openSession(AppUser user, AuthMethod method, ClientInfo client) {
        Instant now = Instant.now();
        String raw = TokenHasher.newRawToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(TokenHasher.sha256Hex(raw));
        rt.setAuthMethod(method);
        rt.setCreatedAt(now);
        rt.setExpiresAt(now.plus(tokenIssuer.refreshTokenTtl()));
        ClientInfo c = client == null ? ClientInfo.UNKNOWN : client;
        rt.setClientIp(c.ip());
        rt.setUserAgent(c.userAgent());
        RefreshToken saved = refreshRepo.save(rt);
        IssuedAccessToken access = tokenIssuer.issueAccessToken(user, saved.getId());
        return new OpenedSession(new AuthSession(access.token(), raw, access.expiresInSeconds(), user), saved.getId());
    }

    private java.util.Optional<RefreshToken> findToken(String raw) {
        String t = Texts.trimToNull(raw);
        if (t == null) {
            return java.util.Optional.empty();
        }
        return refreshRepo.findByTokenHash(TokenHasher.sha256Hex(t));
    }

    private static void ensureActive(AppUser user) {
        if (!user.isActive()) {
            log.warn("[AUTH] Login vào tài khoản đã khoá: {}", user.getUsername());
            throw lockedError();
        }
    }

    private static LaoadsException badCredentials() {
        return LaoadsException.withKey(ErrorCode.UNAUTHORIZED, "error.UNAUTHORIZED.badCredentials",
                "Sai tên đăng nhập hoặc mật khẩu");
    }

    private static LaoadsException refreshInvalid() {
        return LaoadsException.withKey(ErrorCode.UNAUTHORIZED, "error.UNAUTHORIZED.refreshInvalid",
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
    }

    private static LaoadsException lockedError() {
        return new LaoadsException(ErrorCode.USER_LOCKED, "Tài khoản đã bị khoá");
    }
}
