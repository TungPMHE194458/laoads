package com.unitel.laoads.domain.workspace;

import com.unitel.laoads.domain.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * LADS_WORKSPACE_MEMBER — 1 user / 1 workspace (unique). Gỡ thành viên = status REVOKED (soft-delete);
 * thêm lại user đã REVOKED sẽ kích hoạt lại chính bản ghi này.
 */
@Entity
@Table(name = "LADS_WORKSPACE_MEMBER")
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_workspace_member_seq")
    @SequenceGenerator(name = "lads_workspace_member_seq", sequenceName = "LADS_WORKSPACE_MEMBER_SEQ",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    /** EAGER: DTO thành viên luôn cần username/fullName/email/phone. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "MEMBER_ROLE", nullable = false, length = 16)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    private MemberStatus status;

    @Column(name = "JOINED_AT", nullable = false)
    private Instant joinedAt;

    @Column(name = "ADDED_BY", length = 64)
    private String addedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    @Column(name = "REVOKED_AT")
    private Instant revokedAt;

    @Column(name = "REVOKED_BY", length = 64)
    private String revokedBy;

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }
}
