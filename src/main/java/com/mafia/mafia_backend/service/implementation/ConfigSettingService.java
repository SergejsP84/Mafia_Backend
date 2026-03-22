package com.mafia.mafia_backend.service.implementation;
import com.mafia.mafia_backend.domain.entity.ConfigSetting;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.service.interfaces.ConfigSettingServiceInterface;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConfigSettingService implements ConfigSettingServiceInterface {
    private final ConfigSettingRepository configSettingRepository;

    public ConfigSettingService(ConfigSettingRepository configSettingRepository) {
        this.configSettingRepository = configSettingRepository;
    }

    @Override
    public int getIntSetting(String settingName, int defaultValue) {
        return configSettingRepository.findByName(settingName)
                .map(ConfigSetting::getIntvalue)
                .orElse(defaultValue);
    }

    @Override
    public boolean getBooleanSetting(String settingName, boolean defaultValue) {
        Optional<ConfigSetting> optionalProperSetting = configSettingRepository.findByName(settingName);
        return optionalProperSetting.map(ConfigSetting::isEnabled).orElse(defaultValue);

    }

    public Map<String, Object> getAllSettingsAsMap() {
        List<ConfigSetting> all = configSettingRepository.findAll()
                .stream()
                .filter(cs -> Boolean.TRUE.equals(cs.isEnabled()))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();

        for (ConfigSetting cs : all) {
            Object value = null;

            // Prefer intvalue if not null
            if (cs.getIntvalue() != null) {
                value = cs.getIntvalue();
            }
            // Otherwise use floatvalue
            else if (cs.getFloatvalue() != null) {
                value = cs.getFloatvalue();
            }
            // Otherwise, fall back to enabled flag (as 1/0 for simplicity)
            else {
                value = Boolean.TRUE.equals(cs.isEnabled()) ? 1 : 0;
            }

            result.put(cs.getName(), value);
        }

        return result;
    }
}
