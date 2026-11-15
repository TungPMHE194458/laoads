package com.unitel.laoads.web.bootstrap;

import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserStatus;
import com.unitel.laoads.web.bootstrap.AdminBootstrap.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private AppUserRepository repo;
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        repo = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        bootstrap = new AdminBootstrap(repo, encoder, mock(PlatformTransactionManager.class), new MockEnvironment());
    }

    @Test
    void createsFirstSuperAdminWithNormalizedUsername() {
        when(repo.existsBySystemRole(SystemRole.SUPER_ADMIN)).thenReturn(false);

        assertThat(bootstrap.ensureSuperAdmin("  Admin.Unitel ", "S3cure-pass", "Quản trị")).isEqualTo(Result.CREATED);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin.unitel");
        assertThat(saved.getValue().getSystemRole()).isEqualTo(SystemRole.SUPER_ADMIN);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash:S3cure-pass");
    }

    @Test
    void neverTouchesSystemThatAlreadyHasSuperAdmin() {
        when(repo.existsBySystemRole(SystemRole.SUPER_ADMIN)).thenReturn(true);

        assertThat(bootstrap.ensureSuperAdmin("admin", "S3cure-pass", null)).isEqualTo(Result.ALREADY_HAS_SUPER_ADMIN);
        verify(repo, never()).save(any());
    }

    @Test
    void skipsWhenNotConfigured() {
        assertThat(bootstrap.ensureSuperAdmin(null, null, null)).isEqualTo(Result.NOT_CONFIGURED);
        assertThat(bootstrap.ensureSuperAdmin("admin", " ", null)).isEqualTo(Result.NOT_CONFIGURED);
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsShortPasswordAndExistingNonAdminUsername() {
        assertThat(bootstrap.ensureSuperAdmin("admin", "short", null)).isEqualTo(Result.INVALID_CONFIG);

        when(repo.existsByUsername("owner_bsd")).thenReturn(true);
        assertThat(bootstrap.ensureSuperAdmin("owner_bsd", "S3cure-pass", null)).isEqualTo(Result.INVALID_CONFIG);
        verify(repo, never()).save(any());
    }
}
