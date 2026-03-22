package com.mafia.mafia_backend.service.interfaces;

import java.util.Map;

public interface ConfigSettingServiceInterface {
    public int getIntSetting(String settingName, int defaultValue);
    public boolean getBooleanSetting(String settingName, boolean defaultValue);
    public Map<String, Object> getAllSettingsAsMap();
}
