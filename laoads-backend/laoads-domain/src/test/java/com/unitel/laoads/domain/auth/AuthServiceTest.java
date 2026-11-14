package com.unitel.laoads.domain.auth;

import com.unitel.laoads.domain.auth.vsa.VsaClient;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    AppUserRepository userRepo;
    @Mock
    RefreshTokenRepository refreshRepo;
    @Mock
    PasswordEncoder encoder;
    @Mock
    TokenIssuer tokenIssuer;
    @Mock
    VsaClient vsa;
    @Mock
    PlatformTransactionManager txManager;

    AuthService service;
    final AtomicLong seq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepo, refreshRepo, encoder, tokenIssuer, vsa, txManager);
        when(tokenIssuer.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
        when(tokenIssuer.issueAccessToken(any(), any())).thenReturn(new IssuedAccessToken("jwt", 1800));
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            t.setId(seq.incrementAndGet());
            return t;
        });
        when(encoder.matches("right", "HASH")).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("NEWHASH");
    }

    @Test
    void loginNormalizesUsernameAndStoresHashedRefreshToken() {
        AppUser u = user(UserStatus.ACTIVE);
        when(userRepo.findByUsername("owner_bsd")).thenReturn(Optional.of(u));

        AuthSession s = service.login("  Owner_BSD ", "right", ClientInfo.UNKNOWN);

        assertThat(s.accessToken()).isEqualTo("jwt");
        assertThat(s.expiresInSeconds()).isEqualTo(1800);
        assertThat(u.getLastLoginAt()).isNotNull();
        verify(refreshRepo).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.getTokenHash().equals(TokenHasher.sha256Hex(s.refreshToken()))
                        && !t.getTokenHash().equals(s.refreshToken())));
    }

    @Test
    void wrongPasswordIsUnauthorizedAndLockedIsUserLocked() {
        when(userRepo.findByUsername("owner_bsd")).thenReturn(Optional.of(user(UserStatus.LOCKED)));
        assertCode(() -> service.login("owner_bsd", "wrong", null), ErrorCode.UNAUTHORIZED);
        assertCode(() -> service.login("owner_bsd", "right", null), ErrorCode.USER_LOCKED);
        assertCode(() -> service.login("ghost", "right", null), ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh xoay vòng: token cũ bị thu hồi + trỏ replacedBy sang token mới; dùng lại → 401")
    void refreshRotates() {
        AppUser u = user(UserStatus.ACTIVE);
        RefreshToken old = token(5L, "old-raw", Instant.now().plusSeconds(3600));
        when(refreshRepo.findByTokenHash(TokenHasher.sha256Hex("old-raw"))).thenReturn(Optional.of(old));
        when(userRepo.findById(1L)).thenReturn(Optional.of(u));

        AuthSession next = service.refresh("old-raw", null);

        assertThat(next.refreshToken()).isNotEqualTo("old-raw");
        assertThat(old.getRevokedAt()).isNotNull();
        assertThat(old.getReplacedBy()).isEqualTo(seq.get());
        assertCode(() -> service.refresh("old-raw", null), ErrorCode.UNAUTHORIZED);
    }

    @Test
    void expiredOrUnknownRefreshIsUnauthorized() {
        RefreshToken expired = token(6L, "exp", Instant.now().minusSeconds(1));
        when(refreshRepo.findByTokenHash(TokenHasher.sha256Hex("exp"))).thenReturn(Optional.of(expired));
        assertCode(() -> service.refresh("exp", null), ErrorCode.UNAUTHORIZED);
        assertCode(() -> service.refresh("nope", null), ErrorCode.UNAUTHORIZED);
    }

    @Test
    void changePasswordWrongOldIs400AndKeepsCurrentSession() {
        AppUser u = user(UserStatus.ACTIVE);
        when(userRepo.findById(1L)).thenReturn(Optional.of(u));
        AuthUser actor = new AuthUser(1L, "owner_bsd", SystemRole.USER, 55L);

        assertCode(() -> service.changePassword(actor, "wrong", "newpass1"), ErrorCode.WRONG_PASSWORD);
        verify(refreshRepo, never()).revokeOthersOfUser(anyLong(), anyLong(), any());

        service.changePassword(actor, "right", "newpass1");
        assertThat(u.getPasswordHash()).isEqualTo("NEWHASH");
        verify(refreshRepo).revokeOthersOfUser(eq(1L), eq(55L), any(Instant.class));
    }

    @Test
    @DisplayName("VSA tắt → VSA_DISABLED mà không chạm DB / CAS (đúng cả khi DB down)")
    void vsaDisabledDoesNotTouchDb() {
        when(vsa.isEnabled()).thenReturn(false);
        assertCode(() -> service.vsaLogin("ST-1", null, null), ErrorCode.VSA_DISABLED);
        verifyNoInteractions(userRepo, refreshRepo, txManager);
    }

    @Test
    void vsaUserMustBeProvisionedAndActive() {
        when(vsa.isEnabled()).thenReturn(true);
        when(vsa.defaultServiceUrl()).thenReturn("http://fe/cb");
        when(vsa.validateTicket("ST-1", "http://fe/cb")).thenReturn("TUNGPM");
        when(userRepo.findByUsername("tungpm")).thenReturn(Optional.empty());
        assertCode(() -> service.vsaLogin("ST-1", null, null), ErrorCode.VSA_USER_NOT_PROVISIONED);

        AppUser u = user(UserStatus.ACTIVE);
        u.setUsername("tungpm");
        when(userRepo.findByUsername("tungpm")).thenReturn(Optional.of(u));
        assertThat(service.vsaLogin("ST-1", "http://fe/cb", null).user()).isSameAs(u);
    }

    @Test
    void vsaServiceMustMatchConfiguredPrefix() {
        when(vsa.isEnabled()).thenReturn(true);
        when(vsa.defaultServiceUrl()).thenReturn("http://fe/cb");
        assertCode(() -> service.vsaLogin("ST-1", "http://evil/cb", null), ErrorCode.INVALID_PARAM);
        verify(vsa, never()).validateTicket(anyString(), anyString());
    }

    private static AppUser user(UserStatus status) {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setUsername("owner_bsd");
        u.setPasswordHash("HASH");
        u.setSystemRole(SystemRole.USER);
        u.setStatus(status);
        return u;
    }

    private static RefreshToken token(long id, String raw, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setId(id);
        t.setUserId(1L);
        t.setTokenHash(TokenHasher.sha256Hex(raw));
        t.setAuthMethod(AuthMethod.PASSWORD);
        t.setCreatedAt(Instant.now());
        t.setExpiresAt(expiresAt);
        return t;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(LaoadsException.class)
                .extracting(e -> ((LaoadsException) e).getCode())
                .isEqualTo(code);
    }
}
