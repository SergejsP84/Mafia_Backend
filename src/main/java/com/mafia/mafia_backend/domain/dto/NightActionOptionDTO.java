package com.mafia.mafia_backend.domain.dto;

import com.mafia.mafia_backend.domain.enums.ActionType;

public record NightActionOptionDTO(
        String code,          // Internal code, e.g. "KILL", "CHECK"
        String label,         // UI label, localized
        ActionType type,      // How to display target selector
        Integer tierRequired, // Minimum tier to unlock
        Integer cost,         // Optional cost (money)
        Integer usesLeft,     // Optional remaining uses
        boolean available,    // If false, grey out
        String unavailableReason // Optional text explaining why disabled
) {}