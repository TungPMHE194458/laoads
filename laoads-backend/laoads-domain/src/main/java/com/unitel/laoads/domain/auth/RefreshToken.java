package com.unitel.laoads.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * LADS_REFRESH_TOKEN — chỉ lưu SHA-256 của refresh token (không lưu raw).
 * Xoay vòng: mỗi lần /auth/refresh thu hồi token cũ ({@code revokedAt}) và trỏ {@code replacedBy} sang token mới.
 */
@Entity
@Table(name = "LADS_REFRESH_TOKEN")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_refresh_token_seq")
    @SequenceGenerator(name = "lads_refresh_token_seq", sequenceName = "LADS_REFRESH_TOKEN_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TOKEN_HASH", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "AUTH_METHOD", nullable = false, length = 16)
    private AuthMethod authMethod;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "REVOKED_AT")
    private Instant revokedAt;

    @Column(name = "REPLACED_BY")
    private Long replacedBy;

    @Column(name = "CLIENT_IP", length = 64)
    private String clientIp;

    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
