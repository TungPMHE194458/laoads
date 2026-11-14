package com.unitel.laoads.domain.workspace;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import com.unitel.laoads.domain.user.SystemRole;
import com.unitel.laoads.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quy tắc thành viên SRS 3.4 / contract §2 qua MemberService (repo mock, không DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

    static final long WS = 10L;

    @Mock
    WorkspaceRepository workspaceRepo;
    @Mock
    WorkspaceMemberRepository memberRepo;
    @Mock
    AppUserRepository userRepo;

    MemberService service;

    final AppUser owner = user(1L, "owner_bsd");
    final AppUser admin = user(2L, "admin_bsd");
    final AppUser member = user(3L, "member_bsd");
    final AppUser outsider = user(4L, "outsider");

    @BeforeEach
    void setUp() {
        service = new MemberService(workspaceRepo, memberRepo, userRepo);
        when(workspaceRepo.existsById(WS)).thenReturn(true);
        when(memberRepo.save(any(WorkspaceMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepo.findByEmailLower(anyString())).thenReturn(List.of());
        when(userRepo.findByPhoneOrderByIdAsc(anyString())).thenReturn(List.of());
        when(memberRepo.findByWorkspaceIdAndUser_Id(anyLong(), anyLong())).thenReturn(Optional.empty());
        // Thành viên hiện có của workspace
        membership(100L, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
        membership(200L, admin, MemberRole.ADMIN, MemberStatus.ACTIVE);
        membership(300L, member, MemberRole.MEMBER, MemberStatus.ACTIVE);
    }

    @Nested
    @DisplayName("Thêm thành viên")
    class Add {

        @Test
        void adminCannotGrantOwner() {
            knownUser(outsider);
            assertCode(() -> service.add(actor(admin), WS, "outsider", MemberRole.OWNER), ErrorCode.OWNER_ONLY);
            verify(memberRepo, never()).save(any());
        }

        @Test
        void ownerCanGrantOwner() {
            knownUser(outsider);
            WorkspaceMember m = service.add(actor(owner), WS, "outsider", MemberRole.OWNER);
            assertThat(m.getRole()).isEqualTo(MemberRole.OWNER);
            assertThat(m.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(m.getAddedBy()).isEqualTo("owner_bsd");
        }

        @Test
        void superAdminActsAsOwnerEvenWithoutMembership() {
            knownUser(outsider);
            AuthUser sa = new AuthUser(99L, "superadmin", SystemRole.SUPER_ADMIN, null);
            assertThat(service.add(sa, WS, "outsider", MemberRole.OWNER).getRole()).isEqualTo(MemberRole.OWNER);
        }

        @Test
        void duplicateActiveMemberRejected() {
            knownUser(member);
            assertCode(() -> service.add(actor(admin), WS, "member_bsd", MemberRole.MEMBER),
                    ErrorCode.MEMBER_DUPLICATE);
        }

        @Test
        void revokedMemberIsReactivatedInPlace() {
            knownUser(outsider);
            WorkspaceMember revoked = membership(400L, outsider, MemberRole.ADMIN, MemberStatus.REVOKED);
            revoked.setRevokedAt(Instant.now());
            WorkspaceMember m = service.add(actor(admin), WS, "outsider", MemberRole.MEMBER);
            assertThat(m).isSameAs(revoked);
            assertThat(m.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(m.getRole()).isEqualTo(MemberRole.MEMBER);
            assertThat(m.getRevokedAt()).isNull();
        }

        @Test
        void unknownIdentifierIsUserNotFound() {
            assertCode(() -> service.add(actor(admin), WS, "ghost", MemberRole.MEMBER), ErrorCode.USER_NOT_FOUND);
        }

        @Test
        void identifierMatchesEmailCaseInsensitive() {
            outsider.setEmail("Outsider@Unitel.la");
            when(userRepo.findByEmailLower("outsider@unitel.la")).thenReturn(List.of(outsider));
            WorkspaceMember m = service.add(actor(admin), WS, "OUTSIDER@unitel.la", MemberRole.MEMBER);
            assertThat(m.getUser()).isSameAs(outsider);
        }

        @Test
        void unknownWorkspaceIsNotFound() {
            when(workspaceRepo.existsById(WS)).thenReturn(false);
            assertCode(() -> service.add(actor(owner), WS, "outsider", MemberRole.MEMBER), ErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Đổi vai trò")
    class ChangeRole {

        @Test
        void cannotChangeOwnRole() {
            assertCode(() -> service.changeRole(actor(admin), WS, 200L, MemberRole.MEMBER),
                    ErrorCode.CANNOT_CHANGE_SELF);
        }

        @Test
        void adminCannotPromoteToOwner() {
            assertCode(() -> service.changeRole(actor(admin), WS, 300L, MemberRole.OWNER), ErrorCode.OWNER_ONLY);
        }

        @Test
        void adminCannotDemoteOwner() {
            assertCode(() -> service.changeRole(actor(admin), WS, 100L, MemberRole.MEMBER), ErrorCode.OWNER_ONLY);
        }

        @Test
        void ownerCanPromoteMemberToAdmin() {
            WorkspaceMember m = service.changeRole(actor(owner), WS, 300L, MemberRole.ADMIN);
            assertThat(m.getRole()).isEqualTo(MemberRole.ADMIN);
            assertThat(m.getUpdatedBy()).isEqualTo("owner_bsd");
        }

        @Test
        void adminCanChangeMemberToAdmin() {
            assertThat(service.changeRole(actor(admin), WS, 300L, MemberRole.ADMIN).getRole())
                    .isEqualTo(MemberRole.ADMIN);
        }

        @Test
        void revokedMemberIsNotFound() {
            membership(500L, outsider, MemberRole.MEMBER, MemberStatus.REVOKED);
            assertCode(() -> service.changeRole(actor(owner), WS, 500L, MemberRole.ADMIN), ErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Gỡ thành viên")
    class Remove {

        @Test
        void cannotRemoveOwner() {
            assertCode(() -> service.remove(actor(admin), WS, 100L), ErrorCode.CANNOT_REMOVE_OWNER);
        }

        @Test
        void cannotRemoveSelf() {
            assertCode(() -> service.remove(actor(admin), WS, 200L), ErrorCode.CANNOT_REMOVE_SELF);
        }

        @Test
        void removeSetsRevoked() {
            WorkspaceMember m = service.remove(actor(admin), WS, 300L);
            assertThat(m.getStatus()).isEqualTo(MemberStatus.REVOKED);
            assertThat(m.getRevokedBy()).isEqualTo("admin_bsd");
            assertThat(m.getRevokedAt()).isNotNull();
        }

        @Test
        void unknownMemberIsNotFound() {
            assertCode(() -> service.remove(actor(owner), WS, 999L), ErrorCode.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("MemberRules thuần: OWNER_ONLY khi actor không phải thành viên (null)")
    void rulesNonMemberActor() {
        assertCode(() -> MemberRules.checkAssign(null, MemberRole.OWNER), ErrorCode.OWNER_ONLY);
        MemberRules.checkAssign(null, MemberRole.MEMBER); // không ném
    }

    // ---------------------------------------------------------------- helpers

    private static AppUser user(long id, String username) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(username);
        u.setSystemRole(SystemRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private static AuthUser actor(AppUser u) {
        return new AuthUser(u.getId(), u.getUsername(), u.getSystemRole(), null);
    }

    private void knownUser(AppUser u) {
        when(userRepo.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
    }

    private WorkspaceMember membership(long id, AppUser u, MemberRole role, MemberStatus status) {
        WorkspaceMember m = new WorkspaceMember();
        m.setId(id);
        m.setWorkspaceId(WS);
        m.setUser(u);
        m.setRole(role);
        m.setStatus(status);
        m.setJoinedAt(Instant.now());
        when(memberRepo.findByWorkspaceIdAndUser_Id(WS, u.getId())).thenReturn(Optional.of(m));
        when(memberRepo.findByIdAndWorkspaceId(id, WS)).thenReturn(Optional.of(m));
        return m;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(LaoadsException.class)
                .extracting(e -> ((LaoadsException) e).getCode())
                .isEqualTo(code);
    }
}
