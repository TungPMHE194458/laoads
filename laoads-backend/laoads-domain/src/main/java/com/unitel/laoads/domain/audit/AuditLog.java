package com.unitel.laoads.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * LADS_AUDIT_LOG — nhật ký thao tác. Chỉ đọc qua JPA; ghi bằng JdbcTemplate ({@link AuditWriter}, async).
 * Không partition (Oracle partitioning là option trả phí — BOC dùng, LaoAds không phụ thuộc).
 */
@Entity
@Table(name = "LADS_AUDIT_LOG")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACTOR", nullable = false, length = 64)
    private String actor;

    @Column(name = "ACTOR_ID")
    private Long actorId;

    @Column(name = "ACTION_TYPE", nullable = false, length = 32)
    private String actionType;

    @Column(name = "ENTITY_TYPE", nullable = false, length = 64)
    private String entityType;

    @Column(name = "ENTITY_ID", length = 64)
    private String entityId;

    @Column(name = "WORKSPACE_ID")
    private Long workspaceId;

    @Column(name = "SUMMARY", length = 1000)
    private String summary;

    @Lob
    @Column(name = "DETAIL_JSON")
    private String detailJson;

    @Column(name = "CLIENT_IP", length = 64)
    private String clientIp;

    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    @Column(name = "REQUEST_ID", length = 64)
    private String requestId;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    /** SUCCESS | FAILURE (SRS 3.1.8). */
    @Column(name = "RESULT", nullable = false, length = 16)
    private String result = AuditWriter.SUCCESS;

    @Column(name = "ERROR_CODE", length = 64)
    private String errorCode;
}
