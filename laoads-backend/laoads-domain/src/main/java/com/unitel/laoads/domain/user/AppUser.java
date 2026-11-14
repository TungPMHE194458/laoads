package com.unitel.laoads.domain.user;

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

/** LADS_USER — tài khoản đăng nhập LaoAds (local password + VSA map theo username). */
@Entity
@Table(name = "LADS_USER")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_user_seq")
    @SequenceGenerator(name = "lads_user_seq", sequenceName = "LADS_USER_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "FULL_NAME", length = 255)
    private String fullName;

    @Column(name = "EMAIL", length = 255)
    private String email;

    @Column(name = "PHONE", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "SYSTEM_ROLE", nullable = false, length = 16)
    private SystemRole systemRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "LAST_LOGIN_AT")
    private Instant lastLoginAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
