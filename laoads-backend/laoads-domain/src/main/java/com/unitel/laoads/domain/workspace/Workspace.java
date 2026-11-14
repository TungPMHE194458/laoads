package com.unitel.laoads.domain.workspace;

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

/** LADS_WORKSPACE — đơn vị/khách hàng sử dụng LaoAds (vd BSD, BCD). */
@Entity
@Table(name = "LADS_WORKSPACE")
@Getter
@Setter
@NoArgsConstructor
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_workspace_seq")
    @SequenceGenerator(name = "lads_workspace_seq", sequenceName = "LADS_WORKSPACE_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODE", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    private WorkspaceStatus status;

    @Column(name = "CONTACT_NAME", length = 255)
    private String contactName;

    @Column(name = "CONTACT_EMAIL", length = 255)
    private String contactEmail;

    @Column(name = "CONTACT_PHONE", length = 32)
    private String contactPhone;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;
}
