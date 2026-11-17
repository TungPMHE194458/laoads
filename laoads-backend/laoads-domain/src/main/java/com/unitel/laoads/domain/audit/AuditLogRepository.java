package com.unitel.laoads.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = "SELECT a FROM AuditLog a WHERE "
            + "(:actor IS NULL OR LOWER(a.actor) LIKE :actor ESCAPE '!') "
            + "AND (:actionType IS NULL OR a.actionType = :actionType) "
            + "AND (:entityType IS NULL OR a.entityType = :entityType) "
            + "AND (:workspaceId IS NULL OR a.workspaceId = :workspaceId) "
            + "AND (:fromTime IS NULL OR a.createdAt >= :fromTime) "
            + "AND (:toTime IS NULL OR a.createdAt < :toTime) "
            + "AND (:result IS NULL OR a.result = :result) "
            + "ORDER BY a.createdAt DESC, a.id DESC",
            countQuery = "SELECT COUNT(a) FROM AuditLog a WHERE "
            + "(:actor IS NULL OR LOWER(a.actor) LIKE :actor ESCAPE '!') "
            + "AND (:actionType IS NULL OR a.actionType = :actionType) "
            + "AND (:entityType IS NULL OR a.entityType = :entityType) "
            + "AND (:workspaceId IS NULL OR a.workspaceId = :workspaceId) "
            + "AND (:fromTime IS NULL OR a.createdAt >= :fromTime) "
            + "AND (:toTime IS NULL OR a.createdAt < :toTime) "
            + "AND (:result IS NULL OR a.result = :result)")
    Page<AuditLog> search(@Param("actor") String actorLike,
                          @Param("actionType") String actionType,
                          @Param("entityType") String entityType,
                          @Param("workspaceId") Long workspaceId,
                          @Param("fromTime") Instant fromTime,
                          @Param("toTime") Instant toTime,
                          @Param("result") String result,
                          Pageable pageable);
}
