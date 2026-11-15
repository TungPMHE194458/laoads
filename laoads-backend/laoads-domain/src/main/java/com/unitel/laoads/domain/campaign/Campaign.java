package com.unitel.laoads.domain.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * LADS_CAMPAIGN. Cấu hình chi tiết ở {@code definitionJson} ({@link CampaignDefinition}); các bộ đếm
 * (sent/delivered/open/click/conversion/spent) do engine tổng hợp định kỳ từ LADS_CAMPAIGN_MESSAGE / _TARGET.
 */
@Entity
@Table(name = "LADS_CAMPAIGN")
@Getter
@Setter
@NoArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_campaign_seq")
    @SequenceGenerator(name = "lads_campaign_seq", sequenceName = "LADS_CAMPAIGN_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "CODE", nullable = false, length = 32)
    private String code;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "RUN_MODE", nullable = false, length = 16)
    private CampaignEnums.Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "CAMPAIGN_TYPE", nullable = false, length = 16)
    private CampaignEnums.Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CampaignEnums.Status status;

    @Column(name = "GROUP_ID")
    private Long groupId;

    @Column(name = "SEGMENT_ID")
    private Long segmentId;

    @Column(name = "TARGET_COUNT", nullable = false)
    private Integer targetCount = 0;

    /** Danh sách kênh truyền thông, phân tách dấu phẩy (SMS,WHATSAPP…). */
    @Column(name = "CHANNELS", length = 100)
    private String channels;

    @Column(name = "START_AT")
    private Instant startAt;

    @Column(name = "END_AT")
    private Instant endAt;

    @Column(name = "LANGUAGE", length = 4)
    private String language;

    /** Danh sách số thử nghiệm, phân tách dấu phẩy. */
    @Column(name = "TEST_ISDNS", length = 4000)
    private String testIsdns;

    @Lob
    @Column(name = "DEFINITION_JSON", nullable = false)
    private String definitionJson;

    @Column(name = "EXPECTED_COST", nullable = false)
    private BigDecimal expectedCost = BigDecimal.ZERO;

    @Column(name = "BUDGET_MAX")
    private BigDecimal budgetMax;

    @Column(name = "REJECT_REASON", length = 1000)
    private String rejectReason;

    @Column(name = "SENT_COUNT", nullable = false)
    private Integer sentCount = 0;

    @Column(name = "DELIVERED_COUNT", nullable = false)
    private Integer deliveredCount = 0;

    @Column(name = "FAILED_COUNT", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "OPEN_COUNT", nullable = false)
    private Integer openCount = 0;

    @Column(name = "CLICK_COUNT", nullable = false)
    private Integer clickCount = 0;

    @Column(name = "CONVERSION_COUNT", nullable = false)
    private Integer conversionCount = 0;

    /** Số thuê bao (không thuộc nhóm đối chứng) đã nhận ít nhất một tin thành công. */
    @Column(name = "REACHED_COUNT", nullable = false)
    private Integer reachedCount = 0;

    @Column(name = "SPENT", nullable = false)
    private BigDecimal spent = BigDecimal.ZERO;

    /** Chênh lệch tỷ lệ chuyển đổi nhóm nhận tin so với nhóm đối chứng (điểm %). */
    @Column(name = "UPLIFT")
    private BigDecimal uplift;

    @Column(name = "NEXT_RUN_AT")
    private Instant nextRunAt;

    @Column(name = "RUN_COUNT", nullable = false)
    private Integer runCount = 0;

    @Column(name = "SUBMITTED_AT")
    private Instant submittedAt;

    @Column(name = "APPROVED_AT")
    private Instant approvedAt;

    @Column(name = "APPROVED_BY", length = 64)
    private String approvedBy;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version = 0;

    public boolean is(CampaignEnums.Status s) {
        return status == s;
    }

    /** Template sự kiện của chiến dịch EVENT — FE lưu trong description dạng "EVT:SIM_ACTIVATION_D0". */
    public String eventTemplate() {
        return description != null && description.startsWith("EVT:") ? description.substring(4).trim() : null;
    }
}
