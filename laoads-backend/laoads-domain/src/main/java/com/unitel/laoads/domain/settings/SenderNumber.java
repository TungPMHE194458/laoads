package com.unitel.laoads.domain.settings;

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

/** LADS_SENDER_NUMBER — danh mục đầu số / brandname gửi SMS (yêu cầu nâng cấp 2.0). */
@Entity
@Table(name = "LADS_SENDER_NUMBER")
@Getter
@Setter
@NoArgsConstructor
public class SenderNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_sender_number_seq")
    @SequenceGenerator(name = "lads_sender_number_seq", sequenceName = "LADS_SENDER_NUMBER_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODE", nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", nullable = false, length = 16)
    private SenderKind kind;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "ENABLED", nullable = false)
    private Boolean enabled;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;
}
