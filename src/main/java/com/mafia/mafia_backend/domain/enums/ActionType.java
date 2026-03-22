package com.mafia.mafia_backend.domain.enums;

/**
 * Defines the target type for a night action.
 * This helps both backend logic and frontend know
 * what kind of selection (if any) is needed.
 */
public enum ActionType {

    /**
     * The action requires choosing a living player as a target.
     * Example: Mafia KILL, Sheriff CHECK.
     */
    TARGET_PLAYER,

    /**
     * The action targets a dead player (e.g., Necromancer RAISE).
     */
    TARGET_DEAD_PLAYER,

    /**
     * The action targets a specific role rather than a player.
     * Example: Sheriff KILL_ROLE (kill a Mafia/Maniac, etc.).
     */
    TARGET_ROLE,

    /**
     * The action does not require any target selection.
     * Example: Doctor NIGHT_SHIFT, Broad ORGY.
     */
    GLOBAL
}