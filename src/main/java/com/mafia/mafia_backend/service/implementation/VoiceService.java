package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.VoiceResponse;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.springframework.stereotype.Service;

@Service
public class VoiceService {

    public static final int MINIMUM_TIER = 3;
    public static final int MAX_MESSAGE_LENGTH = 256;

    public VoiceResponse voice(GameSessionRuntime game, Long userId, String rawMessage) {
        PlayerInGame player = game.findPlayerById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found in the game."));
        String message = normalizeMessage(rawMessage);
        validateVoiceEligibility(game, player);

        String roleName = currentRoleName(player);
        game.addPublicMessage(roleName + ": " + message);
        game.addLog("Voice submitted by user " + userId + " as role " + roleName + ".");

        return new VoiceResponse(roleName, message);
    }

    public boolean canVoice(GameSessionRuntime game, PlayerInGame player) {
        return voiceUnavailableReason(game, player) == null;
    }

    public void validateVoiceEligibility(GameSessionRuntime game, PlayerInGame player) {
        String reason = voiceUnavailableReason(game, player);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
    }

    public String voiceUnavailableReason(GameSessionRuntime game, PlayerInGame player) {
        if (game == null) {
            return "Game is required.";
        }
        if (!isActiveGameplayPhase(game)) {
            return "Voice is available only during active gameplay.";
        }
        if (player == null || player.getUser() == null) {
            return "Player is required.";
        }
        if (player.getRole() == null || player.getRole().getRoleName() == null || player.getRole().getRoleName().isBlank()) {
            return "Player has no current role.";
        }
        if (player.getTier() < MINIMUM_TIER) {
            return "Voice requires Tier " + MINIMUM_TIER + " or higher.";
        }

        String roleName = player.getRole().getRoleName();
        if (isGhost(roleName)) {
            return "Ghost cannot use Voice.";
        }
        if (isFutureNightOnlyUndeadVoiceRole(roleName)) {
            return game.getStage() == GamePhase.NIGHT ? null : roleName + " can use Voice only at night.";
        }
        if (!player.isAlive()) {
            return "Dead ordinary players cannot use Voice.";
        }
        if (isCleanTownsfolk(roleName)) {
            return "Townsfolk cannot use Voice.";
        }

        return null;
    }

    public String normalizeMessage(String rawMessage) {
        if (rawMessage == null) {
            throw new IllegalArgumentException("Voice message is required.");
        }

        String trimmed = rawMessage.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Voice message cannot be blank.");
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Voice message cannot exceed " + MAX_MESSAGE_LENGTH + " characters.");
        }

        return trimmed;
    }

    private boolean isActiveGameplayPhase(GameSessionRuntime game) {
        if (game.isFinished()) {
            return false;
        }
        return switch (game.getStage()) {
            case LOBBY, ROLE_ASSIGNMENT, ENDED, CANCELED -> false;
            default -> true;
        };
    }

    private String currentRoleName(PlayerInGame player) {
        return player.getRole().getRoleName();
    }

    private boolean isCleanTownsfolk(String roleName) {
        return roleName.equalsIgnoreCase("townsfolk");
    }

    private boolean isGhost(String roleName) {
        return roleName.equalsIgnoreCase("ghost");
    }

    private boolean isFutureNightOnlyUndeadVoiceRole(String roleName) {
        return roleName.equalsIgnoreCase("vampire") || roleName.equalsIgnoreCase("demon");
    }
}
