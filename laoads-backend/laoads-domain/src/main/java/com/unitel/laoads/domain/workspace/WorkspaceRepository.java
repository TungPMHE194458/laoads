package com.unitel.laoads.domain.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    boolean existsByCode(String code);

    Optional<Workspace> findByCode(String code);

    List<Workspace> findByStatusOrderByNameAsc(WorkspaceStatus status);

    @Query(value = "SELECT w FROM Workspace w WHERE "
            + "(:q IS NULL OR LOWER(w.code) LIKE :q ESCAPE '!' OR LOWER(w.name) LIKE :q ESCAPE '!') "
            + "AND (:status IS NULL OR w.status = :status) "
            + "ORDER BY w.createdAt DESC, w.id DESC",
            countQuery = "SELECT COUNT(w) FROM Workspace w WHERE "
            + "(:q IS NULL OR LOWER(w.code) LIKE :q ESCAPE '!' OR LOWER(w.name) LIKE :q ESCAPE '!') "
            + "AND (:status IS NULL OR w.status = :status)")
    Page<Workspace> search(@Param("q") String likePattern,
                           @Param("status") WorkspaceStatus status,
                           Pageable pageable);
}
