package com.unitel.laoads.domain.workspace;

import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.permission.PermissionEvaluator;
import com.unitel.laoads.domain.user.AppUser;
import com.unitel.laoads.domain.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository memberRepo;
    private final AppUserRepository userRepo;
    private final PermissionEvaluator permissionEvaluator;

    /**
     * Workspace của tôi (chỉ workspace ACTIVE):
     * user thường = các membership ACTIVE; SUPER_ADMIN = mọi workspace ACTIVE (role null nếu không là thành viên).
     */
    @Transactional(readOnly = true)
    public List<MyWorkspaceView> myWorkspaces(AuthUser user) {
        Map<Long, MemberRole> roleByWs = new HashMap<>();
        for (WorkspaceMember m : memberRepo.findByUser_IdAndStatus(user.id(), MemberStatus.ACTIVE)) {
            roleByWs.put(m.getWorkspaceId(), m.getRole());
        }
        List<Workspace> candidates = user.isSuperAdmin()
                ? workspaceRepo.findByStatusOrderByNameAsc(WorkspaceStatus.ACTIVE)
                : workspaceRepo.findAllById(roleByWs.keySet()).stream()
                        .filter(w -> w.getStatus() == WorkspaceStatus.ACTIVE)
                        .sorted(Comparator.comparing(Workspace::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        List<MyWorkspaceView> out = new ArrayList<>(candidates.size());
        for (Workspace w : candidates) {
            MemberRole role = roleByWs.get(w.getId());
            out.add(new MyWorkspaceView(w, role, permissionEvaluator.effectivePermissions(user, role)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Workspace get(long id) {
        return workspaceRepo.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public long memberCount(long workspaceId) {
        return memberRepo.countByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE);
    }

    /** Số thành viên ACTIVE cho nhiều workspace (1 query GROUP BY). */
    @Transactional(readOnly = true)
    public Map<Long, Long> memberCounts(Collection<Long> workspaceIds) {
        Map<Long, Long> out = new HashMap<>();
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return out;
        }
        for (Object[] row : memberRepo.countByWorkspaceIds(workspaceIds, MemberStatus.ACTIVE)) {
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return out;
    }

    @Transactional
    public Workspace update(long id, String name, String description, String contactName,
                            String contactEmail, String contactPhone, AuthUser actor) {
        Workspace w = get(id);
        w.setName(name.trim());
        w.setDescription(Texts.trimToNull(description));
        w.setContactName(Texts.trimToNull(contactName));
        w.setContactEmail(Texts.trimToNull(contactEmail));
        w.setContactPhone(Texts.trimToNull(contactPhone));
        w.setUpdatedAt(Instant.now());
        w.setUpdatedBy(actor.username());
        return w;
    }

    @Transactional(readOnly = true)
    public Page<Workspace> adminSearch(String query, WorkspaceStatus status, Pageable pageable) {
        return workspaceRepo.search(Texts.likePattern(query), status, pageable);
    }

    /** SUPER_ADMIN tạo workspace + gán OWNER. Trùng code → 409 WORKSPACE_CODE_DUPLICATE. */
    @Transactional
    public Workspace create(String code, String name, String description, String ownerUsername, AuthUser actor) {
        String normalizedCode = Texts.normalizeCode(code);
        if (workspaceRepo.existsByCode(normalizedCode)) {
            throw new LaoadsException(ErrorCode.WORKSPACE_CODE_DUPLICATE,
                    "Mã workspace " + normalizedCode + " đã tồn tại", normalizedCode);
        }
        AppUser owner = userRepo.findByUsername(Texts.normalizeUsername(ownerUsername))
                .orElseThrow(() -> new LaoadsException(ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy người dùng: " + ownerUsername, ownerUsername));
        Instant now = Instant.now();
        Workspace w = new Workspace();
        w.setCode(normalizedCode);
        w.setName(name.trim());
        w.setDescription(Texts.trimToNull(description));
        w.setStatus(WorkspaceStatus.ACTIVE);
        w.setCreatedAt(now);
        w.setCreatedBy(actor.username());
        Workspace saved = workspaceRepo.save(w);

        WorkspaceMember m = new WorkspaceMember();
        m.setWorkspaceId(saved.getId());
        m.setUser(owner);
        m.setRole(MemberRole.OWNER);
        m.setStatus(MemberStatus.ACTIVE);
        m.setJoinedAt(now);
        m.setAddedBy(actor.username());
        memberRepo.save(m);
        log.info("[WORKSPACE] {} tạo workspace {} (OWNER={})", actor.username(), normalizedCode, owner.getUsername());
        return saved;
    }

    @Transactional
    public Workspace changeStatus(long id, WorkspaceStatus status, AuthUser actor) {
        Workspace w = get(id);
        w.setStatus(status);
        w.setUpdatedAt(Instant.now());
        w.setUpdatedBy(actor.username());
        log.info("[WORKSPACE] {} đổi trạng thái workspace {} → {}", actor.username(), w.getCode(), status);
        return w;
    }

    private static LaoadsException notFound(long id) {
        return LaoadsException.notFound("error.notFound.workspace", "Không tìm thấy workspace id=" + id, id);
    }
}
