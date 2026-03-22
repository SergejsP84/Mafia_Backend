package com.mafia.mafia_backend.bootstrap;

import com.mafia.mafia_backend.domain.entity.ConfigSetting;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfigSettingsInitializer implements ApplicationRunner {

    private final ConfigSettingRepository configSettingRepository;

    @Override
    public void run(ApplicationArguments args) {
        String settingName = "AllowMultipleUsersFromDevice";
        boolean configSettingExists = configSettingRepository.existsByName(settingName);
        if (!configSettingExists) {
            ConfigSetting setting = new ConfigSetting();
            setting.setName(settingName);
            setting.setEnabled(false); // Default value
            setting.setIntvalue(0);    // Default
            setting.setFloatvalue(0f); // Default
            configSettingRepository.save(setting);
            System.out.println("🐽 Config setting '" + settingName + "' created with default values.");
        } else {
            System.out.println("🐽 Config setting '" + settingName + "' already exists.");
        }
        settingName = "LobbyDurationSeconds";
        configSettingExists = configSettingRepository.existsByName(settingName);
        if (!configSettingExists) {
            ConfigSetting setting = new ConfigSetting();
            setting.setName(settingName);
            setting.setEnabled(true);
            setting.setIntvalue(30);    // TODO: Default to 600 before commissioning
            setting.setFloatvalue(0f);
            configSettingRepository.save(setting);
            System.out.println("🐽 Config setting '" + settingName + "' created with default values.");
        } else {
            System.out.println("🐽 Config setting '" + settingName + "' already exists.");
        }

        settingName = "MaxPlayersPerGame";
        configSettingExists = configSettingRepository.existsByName(settingName);
        if (!configSettingExists) {
            ConfigSetting setting = new ConfigSetting();
            setting.setName(settingName);
            setting.setEnabled(true);
            setting.setIntvalue(50);
            setting.setFloatvalue(0f);
            configSettingRepository.save(setting);
            System.out.println("🐽 Config setting '" + settingName + "' created with default values.");
        } else {
            System.out.println("🐽 Config setting '" + settingName + "' already exists.");
        }
        // More to be added here
    }
}
