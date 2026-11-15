package com.unitel.laoads.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** LADS_APP_SETTING — cấu hình hệ thống dạng khoá–giá trị. */
@Entity
@Table(name = "LADS_APP_SETTING")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "SETTING_KEY", length = 64)
    private String key;

    @Column(name = "SETTING_VALUE", length = 1000)
    private String value;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
