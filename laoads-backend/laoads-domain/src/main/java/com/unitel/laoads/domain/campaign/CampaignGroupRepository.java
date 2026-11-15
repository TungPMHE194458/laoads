package com.unitel.laoads.domain.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignGroupRepository extends JpaRepository<CampaignGroup, Long> {

    List<CampaignGroup> findByWorkspaceIdOrderByNameAsc(Long workspaceId);

    Optional<CampaignGroup> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query("SELECT COUNT(g) FROM CampaignGroup g WHERE g.workspaceId = :ws AND LOWER(g.name) = :name "
            + "AND (:exceptId IS NULL OR g.id <> :exceptId)")
    long countByName(@Param("ws") Long workspaceId, @Param("name") String lowerName, @Param("exceptId") Long exceptId);
}
