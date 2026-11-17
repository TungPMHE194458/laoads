package com.unitel.laoads.domain.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** LADS_CAMPAIGN_GROUP — nhóm chiến dịch trong workspace (tên duy nhất, không phân biệt hoa/thường). */
@Entity
@Table(name = "LADS_CAMPAIGN_GROUP")
@Getter
@Setter
@NoArgsConstructor
public class CampaignGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_campaign_group_seq")
    @SequenceGenerator(name = "lads_campaign_group_seq", sequenceName = "LADS_CAMPAIGN_GROUP_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    /** Mô tả nhóm (V7, tối đa 500 ký tự) — như laoads.com. */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;
}
