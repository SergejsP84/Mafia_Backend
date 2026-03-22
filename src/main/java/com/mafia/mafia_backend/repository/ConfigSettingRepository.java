package com.mafia.mafia_backend.repository;

import com.mafia.mafia_backend.domain.entity.ConfigSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigSettingRepository extends JpaRepository<ConfigSetting, Long> {
    boolean existsByName(String name);
    Optional<ConfigSetting> findByName(String name);
}