package com.unitel.laoads.domain.segment;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** LADS_SEGMENT — tệp khách hàng; thành viên nằm ở LADS_SEGMENT_MEMBER (ghi bằng JdbcTemplate theo lô). */
@Entity
@Table(name = "LADS_SEGMENT")
@Getter
@Setter
@NoArgsConstructor
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_segment_seq")
    @SequenceGenerator(name = "lads_segment_seq", sequenceName = "LADS_SEGMENT_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "NAME", nullable = false, length = 255)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "SEGMENT_TYPE", nullable = false, length = 20)
    private SegmentEnums.Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE", nullable = false, length = 16)
    private SegmentEnums.Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    private SegmentEnums.Status status;

    @Column(name = "TOTAL_USERS", nullable = false)
    private Integer totalUsers = 0;

    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    @Lob
    @Column(name = "CONDITIONS_JSON")
    private String conditionsJson;

    /** Tệp AUDIENCE: request gửi Audience API (bảng, cột lấy, điều kiện) — xem domain.audience.AudienceRequest. */
    @Lob
    @Column(name = "AUDIENCE_JSON")
    private String audienceJson;

    /** Tệp AUDIENCE: số dòng NDJSON đã xử lý = offset để nạp tiếp (OCS). */
    @Column(name = "IMPORTED_ROWS", nullable = false)
    private Long importedRows = 0L;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    @Column(name = "SOURCE_CAMPAIGN_ID")
    private Long sourceCampaignId;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    public boolean isReady() {
        return status == SegmentEnums.Status.READY;
    }
}
