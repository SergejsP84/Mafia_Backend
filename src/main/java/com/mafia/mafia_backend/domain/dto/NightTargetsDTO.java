package com.mafia.mafia_backend.domain.dto;

import java.util.List;

public record NightTargetsDTO(
        List<TargetUserDTO> alivePlayers,
        List<TargetUserDTO> deadPlayers,
        List<String> roles // Role names, e.g. "Mafia", "Sheriff"
) {}
