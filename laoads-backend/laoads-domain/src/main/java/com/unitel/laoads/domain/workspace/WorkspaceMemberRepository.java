package com.unitel.laoads.domain.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUser_Id(Long workspaceId, Long userId);

    Optional<WorkspaceMember> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<WorkspaceMember> findByUser_IdAndStatus(Long userId, MemberStatus status);

    long countByWorkspaceIdAndStatus(Long workspaceId, MemberStatus status);

    /**
     * Vai trò của user trong workspace, chỉ khi thành viên ACTIVE và workspace ACTIVE
     * (workspace LOCKED → coi như không có quyền, trừ SUPER_ADMIN).
     */
    @Query("SELECT m.role FROM WorkspaceMember m, Workspace w "
            + "WHERE w.id = m.workspaceId AND m.workspaceId = :workspaceId AND m.user.id = :userId "
            + "AND m.status = :memberStatus AND w.status = :workspaceStatus")
    Optional<MemberRole> findRole(@Param("userId") Long userId,
                                  @Param("workspaceId") Long workspaceId,
                                  @Param("memberStatus") MemberStatus memberStatus,
                                  @Param("workspaceStatus") WorkspaceStatus workspaceStatus);

    /** [workspaceId, count] số thành viên theo trạng thái cho 1 trang workspace. */
    @Query("SELECT m.workspaceId, COUNT(m) FROM WorkspaceMember m "
            + "WHERE m.workspaceId IN :ids AND m.status = :status GROUP BY m.workspaceId")
    List<Object[]> countByWorkspaceIds(@Param("ids") Collection<Long> workspaceIds,
                                       @Param("status") MemberStatus status);

    @Query(value = "SELECT m FROM WorkspaceMember m JOIN FETCH m.user u WHERE m.workspaceId = :workspaceId "
            + "AND (:q IS NULL OR LOWER(u.username) LIKE :q ESCAPE '!' OR LOWER(u.fullName) LIKE :q ESCAPE '!' "
            + "  OR LOWER(u.email) LIKE :q ESCAPE '!') "
            + "AND (:role IS NULL OR m.role = :role) "
            + "AND (:status IS NULL OR m.status = :status) "
            + "ORDER BY m.joinedAt ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) FROM WorkspaceMember m JOIN m.user u WHERE m.workspaceId = :workspaceId "
            + "AND (:q IS NULL OR LOWER(u.username) LIKE :q ESCAPE '!' OR LOWER(u.fullName) LIKE :q ESCAPE '!' "
            + "  OR LOWER(u.email) LIKE :q ESCAPE '!') "
            + "AND (:role IS NULL OR m.role = :role) "
            + "AND (:status IS NULL OR m.status = :status)")
    Page<WorkspaceMember> search(@Param("workspaceId") Long workspaceId,
                                 @Param("q") String likePattern,
                                 @Param("role") MemberRole role,
                                 @Param("status") MemberStatus status,
                                 Pageable pageable);
}
