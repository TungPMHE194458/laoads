package com.unitel.laoads.domain.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /** Thứ tự sắp xếp lấy từ {@code pageable} (CampaignService thêm mặc định createdAt giảm dần). */
    @Query(value = "SELECT c FROM Campaign c WHERE c.workspaceId = :ws "
            + "AND (:q IS NULL OR LOWER(c.name) LIKE :q ESCAPE '!' OR LOWER(c.code) LIKE :q ESCAPE '!') "
            + "AND (:status IS NULL OR c.status = :status) AND (:mode IS NULL OR c.mode = :mode) "
            + "AND (:groupId IS NULL OR c.groupId = :groupId)",
            countQuery = "SELECT COUNT(c) FROM Campaign c WHERE c.workspaceId = :ws "
            + "AND (:q IS NULL OR LOWER(c.name) LIKE :q ESCAPE '!' OR LOWER(c.code) LIKE :q ESCAPE '!') "
            + "AND (:status IS NULL OR c.status = :status) AND (:mode IS NULL OR c.mode = :mode) "
            + "AND (:groupId IS NULL OR c.groupId = :groupId)")
    Page<Campaign> search(@Param("ws") Long workspaceId,
                          @Param("q") String likePattern,
                          @Param("status") CampaignEnums.Status status,
                          @Param("mode") CampaignEnums.Mode mode,
                          @Param("groupId") Long groupId,
                          Pageable pageable);

    /** Trùng tên chiến dịch trong workspace (không phân biệt hoa/thường, bỏ qua chính nó khi sửa) — SRS 3.2.1.4–6. */
    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.workspaceId = :ws AND LOWER(c.name) = :name "
            + "AND (:exceptId IS NULL OR c.id <> :exceptId)")
    long countByName(@Param("ws") Long workspaceId, @Param("name") String lowerName, @Param("exceptId") Long exceptId);

    long countByWorkspaceIdAndStatus(Long workspaceId, CampaignEnums.Status status);

    long countByWorkspaceId(Long workspaceId);

    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.workspaceId = :ws AND c.createdAt < :before")
    long countCreatedBefore(@Param("ws") Long workspaceId, @Param("before") Instant before);

    long countByGroupId(Long groupId);

    /** [groupId, tổng chiến dịch, số đang chạy] theo nhóm trong workspace. */
    @Query("SELECT c.groupId, COUNT(c), SUM(CASE WHEN c.status = com.unitel.laoads.domain.campaign.CampaignEnums.Status.RUNNING "
            + "THEN 1 ELSE 0 END) FROM Campaign c WHERE c.workspaceId = :ws AND c.groupId IS NOT NULL GROUP BY c.groupId")
    List<Object[]> groupCounts(@Param("ws") Long workspaceId);

    /** Chiến dịch tới hạn chạy lượt tiếp theo. */
    @Query("SELECT c.id FROM Campaign c WHERE c.status IN :statuses AND c.nextRunAt IS NOT NULL AND c.nextRunAt <= :now "
            + "ORDER BY c.nextRunAt")
    List<Long> dueForRun(@Param("statuses") Collection<CampaignEnums.Status> statuses, @Param("now") Instant now);

    List<Campaign> findByStatusIn(Collection<CampaignEnums.Status> statuses);

    List<Campaign> findByWorkspaceIdAndTypeAndStatus(Long workspaceId, CampaignEnums.Type type,
                                                     CampaignEnums.Status status);

    /** [workspaceId, tổng chiến dịch, số đang chạy] toàn hệ thống — Dashboard Super Admin. */
    @Query("SELECT c.workspaceId, COUNT(c), SUM(CASE WHEN c.status = com.unitel.laoads.domain.campaign.CampaignEnums.Status.RUNNING "
            + "THEN 1 ELSE 0 END) FROM Campaign c GROUP BY c.workspaceId ORDER BY COUNT(c) DESC")
    List<Object[]> countsByWorkspace();

    @Query("SELECT COUNT(c) FROM Campaign c WHERE c.status = com.unitel.laoads.domain.campaign.CampaignEnums.Status.RUNNING")
    long countRunning();
}
