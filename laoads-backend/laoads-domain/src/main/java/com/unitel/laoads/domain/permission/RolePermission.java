package com.unitel.laoads.domain.permission;

import com.unitel.laoads.domain.workspace.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** LADS_ROLE_PERMISSION — ma trận vai trò workspace → permission (PK = role + permission). */
@Entity
@Table(name = "LADS_ROLE_PERMISSION")
@IdClass(RolePermission.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_CODE", nullable = false, length = 16)
    private MemberRole roleCode;

    @Id
    @Column(name = "PERMISSION_CODE", nullable = false, length = 64)
    private String permissionCode;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    public RolePermission(MemberRole roleCode, String permissionCode, Instant updatedAt, String updatedBy) {
        this.roleCode = roleCode;
        this.permissionCode = permissionCode;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private MemberRole roleCode;
        private String permissionCode;
    }
}
