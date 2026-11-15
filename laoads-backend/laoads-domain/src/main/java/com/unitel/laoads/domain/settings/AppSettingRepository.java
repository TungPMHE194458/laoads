package com.unitel.laoads.domain.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    List<AppSetting> findByKeyStartingWith(String prefix);
}
