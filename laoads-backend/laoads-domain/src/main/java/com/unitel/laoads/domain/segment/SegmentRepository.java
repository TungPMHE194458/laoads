package com.unitel.laoads.domain.segment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    Optional<Segment> findByIdAndWorkspaceId(Long id, Long workspaceId);

    @Query(value = "SELECT s FROM Segment s WHERE s.workspaceId = :ws "
            + "AND (:q IS NULL OR LOWER(s.name) LIKE :q ESCAPE '!') "
            + "AND (:type IS NULL OR s.type = :type) AND (:status IS NULL OR s.status = :status) "
            + "ORDER BY s.createdAt DESC, s.id DESC",
            countQuery = "SELECT COUNT(s) FROM Segment s WHERE s.workspaceId = :ws "
            + "AND (:q IS NULL OR LOWER(s.name) LIKE :q ESCAPE '!') "
            + "AND (:type IS NULL OR s.type = :type) AND (:status IS NULL OR s.status = :status)")
    Page<Segment> search(@Param("ws") Long workspaceId,
                         @Param("q") String likePattern,
                         @Param("type") SegmentEnums.Type type,
                         @Param("status") SegmentEnums.Status status,
                         Pageable pageable);

    @Query("SELECT COUNT(s) FROM Segment s WHERE s.workspaceId = :ws AND LOWER(s.name) = :name")
    long countByName(@Param("ws") Long workspaceId, @Param("name") String lowerName);

    List<Segment> findTop20ByStatusAndTypeOrderByIdAsc(SegmentEnums.Status status, SegmentEnums.Type type);
}
