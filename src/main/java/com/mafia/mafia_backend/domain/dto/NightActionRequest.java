package com.mafia.mafia_backend.domain.dto;


/**
 * Incoming request when a player performs a night action.
 */
public record NightActionRequest(
        String actionCode,
        Long targetUserId,
        String targetRole
) {}
