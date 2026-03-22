package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.service.implementation.ConfigSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigSettingService configSettingService;

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        Map<String, Object> configs = configSettingService.getAllSettingsAsMap();
        return ResponseEntity.ok(configs);
    }
}