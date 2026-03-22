package com.mafia.mafia_backend.domain.dto;

import java.util.List;

/**
 * Bundles together all actions and available targets,
 * plus timing info so UI can show a countdown.
 */
public record NightActionCatalogDTO(
        List<NightActionOptionDTO> actions,
        NightTargetsDTO targets,
        long secondsLeft
) {}
