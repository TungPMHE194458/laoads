package com.unitel.laoads.domain.workspace;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Quản lý thành viên workspace. Quyền MEMBER_MANAGE/WORKSPACE_VIEW đã được {@code @perm} kiểm ở controller;
 * service áp các quy tắc nghiệp vụ trong {@link MemberRules}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository memberRepo;
    private final AppUserRepository userRepo;

    @Transactional(readOnly = true)
    public Page<WorkspaceMember> list(long workspaceId, String query, MemberRole role, MemberStatus status,
                                      Pageable pageable) {
        requireWorkspace(workspaceId);
        return memberRepo.search(workspaceId, Texts.likePattern(query), role, status, pageable);
    }

    /**
     * Thêm thành viên theo username / email / SĐT. User đã ACTIVE → 409 MEMBER_DUPLICATE;
     * user từng bị gỡ (REVOKED) → kích hoạt lại với vai trò mới.
     */
    @Transactional
    public WorkspaceMember add(AuthUser actor, long workspaceId, String identifier, MemberRole role) {
        requireWorkspace(workspaceId);
        MemberRules.checkAssign(actorRole(actor, workspaceId), role);

        AppUser user = findUserByIdentifier(identifier).orElseThrow(() -> new LaoadsException(
                ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng: " + identifier, identifier));

        Instant now = Instant.now();
        Optional<WorkspaceMember> existing = memberRepo.findByWorkspaceIdAndUser_Id(workspaceId, user.getId());
        WorkspaceMember m;
        if (existing.isPresent()) {
            m = existing.get();
            if (m.isActive()) {
                throw new LaoadsException(ErrorCode.MEMBER_DUPLICATE,
                        "Người dùng " + user.getUsername() + " đã là thành viên của workspace", user.getUsername());
            }
            m.setRevokedAt(null);
            m.setRevokedBy(null);
            m.setUpdatedAt(now);
            m.setUpdatedBy(actor.username());
        } else {
            m = new WorkspaceMember();
            m.setWorkspaceId(workspaceId);
            m.setUser(user);
        }
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE);
        m.setJoinedAt(now);
        m.setAddedBy(actor.username());
        WorkspaceMember saved = memberRepo.save(m);
        log.info("[MEMBER] {} thêm {} vào workspace {} với vai trò {}", actor.username(), user.getUsername(),
                workspaceId, role);
        return saved;
    }

    @Transactional
    public WorkspaceMember changeRole(AuthUser actor, long workspaceId, long memberId, MemberRole newRole) {
        WorkspaceMember m = requireActiveMember(workspaceId, memberId);
        MemberRules.checkChangeRole(actor.id(), actorRole(actor, workspaceId),
                m.getUser().getId(), m.getRole(), newRole);
        if (m.getRole() != newRole) {
            log.info("[MEMBER] {} đổi vai trò {} trong workspace {}: {} → {}", actor.username(),
                    m.getUser().getUsername(), workspaceId, m.getRole(), newRole);
            m.setRole(newRole);
            m.setUpdatedAt(Instant.now());
            m.setUpdatedBy(actor.username());
        }
        return m;
    }

    @Transactional
    public WorkspaceMember remove(AuthUser actor, long workspaceId, long memberId) {
        WorkspaceMember m = requireActiveMember(workspaceId, memberId);
        MemberRules.checkRemove(actor.id(), m.getUser().getId(), m.getRole());
        Instant now = Instant.now();
        m.setStatus(MemberStatus.REVOKED);
        m.setRevokedAt(now);
        m.setRevokedBy(actor.username());
        m.setUpdatedAt(now);
        m.setUpdatedBy(actor.username());
        log.info("[MEMBER] {} gỡ {} khỏi workspace {}", actor.username(), m.getUser().getUsername(), workspaceId);
        return m;
    }

    /** Vai trò hiệu lực của người thao tác: SUPER_ADMIN = OWNER (qua mọi kiểm tra quyền). */
    MemberRole actorRole(AuthUser actor, long workspaceId) {
        if (actor.isSuperAdmin()) {
            return MemberRole.OWNER;
        }
        return memberRepo.findByWorkspaceIdAndUser_Id(workspaceId, actor.id())
                .filter(WorkspaceMember::isActive)
                .map(WorkspaceMember::getRole)
                .orElse(null);
    }

    /** identifier = username, hoặc email (không phân biệt hoa thường), hoặc SĐT — theo thứ tự đó. */
    Optional<AppUser> findUserByIdentifier(String identifier) {
        String id = Texts.trimToNull(identifier);
        if (id == null) {
            return Optional.empty();
        }
        Optional<AppUser> byUsername = userRepo.findByUsername(Texts.normalizeUsername(id));
        if (byUsername.isPresent()) {
            return byUsername;
        }
        List<AppUser> byEmail = userRepo.findByEmailLower(id.toLowerCase(java.util.Locale.ROOT));
        if (!byEmail.isEmpty()) {
            return Optional.of(byEmail.get(0));
        }
        List<AppUser> byPhone = userRepo.findByPhoneOrderByIdAsc(id);
        return byPhone.isEmpty() ? Optional.empty() : Optional.of(byPhone.get(0));
    }

    private WorkspaceMember requireActiveMember(long workspaceId, long memberId) {
        return memberRepo.findByIdAndWorkspaceId(memberId, workspaceId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> LaoadsException.notFound("error.notFound.member",
                        "Không tìm thấy thành viên id=" + memberId, memberId));
    }

    private void requireWorkspace(long workspaceId) {
        if (!workspaceRepo.existsById(workspaceId)) {
            throw LaoadsException.notFound("error.notFound.workspace",
                    "Không tìm thấy workspace id=" + workspaceId, workspaceId);
        }
    }
}
