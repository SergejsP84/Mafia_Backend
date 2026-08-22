package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.dto.DigRequest;
import com.mafia.mafia_backend.domain.dto.DigResponse;
import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.dto.NightActionRequest;
import com.mafia.mafia_backend.domain.dto.VoiceRequest;
import com.mafia.mafia_backend.domain.dto.VoiceResponse;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.service.implementation.ActionService;
import com.mafia.mafia_backend.service.implementation.DigService;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import com.mafia.mafia_backend.service.implementation.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;
    private final GameManagerService gameManagerService;
    private final DigService digService;
    private final VoiceService voiceService;

    // helper for getting UUID of the GameSessionRuntime
    private UUID getUuid(Long gameId) {
        List<GameSessionRuntime> gamesInProgress = gameManagerService.getActiveGames();
        for (GameSessionRuntime gsr : gamesInProgress) {
            if (gsr.getGame().getId().equals(gameId)) {
                return gsr.getSessionId();
            }
        }
        return null;
    }

    @PostMapping("/mafia/kill/{gameId}/{actorId}")
    public ResponseEntity<String> mafiaKill(
            @PathVariable Long gameId,
            @PathVariable Long actorId,
            @RequestBody Map<String, Object> payload) {

        UUID sessionId = getUuid(gameId);
        if (sessionId == null)
            return ResponseEntity.badRequest().body("❌ Session not found for game ID " + gameId);

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null)
            return ResponseEntity.badRequest().body("❌ Game not found");

        if (!actionService.isActiveMafia(game, actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only the active Mafia may submit a Mafia action tonight.");
        }

        Object raw = payload.get("targetUserId");
        Long targetId = (raw instanceof Number)
                ? ((Number) raw).longValue()
                : Long.parseLong(raw.toString());

        int nightNumber = game.getCurrentNightNumber();
        NightAction action = new NightAction(actorId, targetId, NightActionType.KILL, nightNumber, extractComment(payload));

        return recordAction(game, action, "💀 Mafia kill action recorded.");
    }

    @PostMapping("/mafia/skip")
    public ResponseEntity<String> mafiaSkip(@RequestParam Long gameId,
                                            @RequestParam Long actorId,
                                            @RequestBody(required = false) Map<String, Object> payload) {

        UUID sessionId = getUuid(gameId);
        if (sessionId == null) {
            return ResponseEntity.badRequest().body("❌ Session not found for game ID " + gameId);
        }

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.badRequest().body("❌ Game not found");

        if (!actionService.isActiveMafia(game, actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only the active Mafia may submit a Mafia action tonight.");
        }

        int nightNumber = game.getCurrentNightNumber();
        NightAction action = new NightAction(actorId, null, NightActionType.SKIP, nightNumber, extractComment(payload));

        return recordAction(game, action, "😴 Mafia skipped their turn.");
    }

    @PostMapping("/sheriff/check/{gameId}/{actorId}")
    public ResponseEntity<String> sheriffCheck(
            @PathVariable Long gameId,
            @PathVariable Long actorId,
            @RequestBody Map<String, Object> payload) {

        UUID sessionId = getUuid(gameId);
        if (sessionId == null) {
            return ResponseEntity.badRequest().body("❌ Session not found for game ID " + gameId);
        }

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null)
            return ResponseEntity.badRequest().body("❌ Game not found");

        Object raw = payload.get("targetUserId");
        Long targetId = (raw instanceof Number)
                ? ((Number) raw).longValue()
                : Long.parseLong(raw.toString());

        int nightNumber = game.getCurrentNightNumber();
        NightAction action = new NightAction(actorId, targetId, NightActionType.CHECK, nightNumber, extractComment(payload));

        return recordAction(game, action, "🔍 Sheriff investigation submitted.");
    }

    @PostMapping("/sheriff/kill/{gameId}/{actorId}")
    public ResponseEntity<String> sheriffKill(
            @PathVariable Long gameId,
            @PathVariable Long actorId,
            @RequestBody Map<String, Object> payload) {

        UUID sessionId = getUuid(gameId);
        if (sessionId == null)
            return ResponseEntity.badRequest().body("❌ Session not found for game ID " + gameId);

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null)
            return ResponseEntity.badRequest().body("❌ Game not found");

        Object raw = payload.get("targetUserId");
        Long targetId = (raw instanceof Number)
                ? ((Number) raw).longValue()
                : Long.parseLong(raw.toString());

        int nightNumber = game.getCurrentNightNumber();
        NightAction action = new NightAction(actorId, targetId, NightActionType.KILL, nightNumber, extractComment(payload));

        return recordAction(game, action, "🔫 Sheriff kill action recorded.");
    }

    @PostMapping("/sheriff/skip")
    public ResponseEntity<String> sheriffSkip(@RequestParam Long gameId,
                                              @RequestParam Long actorId,
                                              @RequestBody(required = false) Map<String, Object> payload) {

        UUID sessionId = getUuid(gameId);
        if (sessionId == null) {
            return ResponseEntity.badRequest().body("❌ Session not found for game ID " + gameId);
        }

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.badRequest().body("❌ Game not found");

        int nightNumber = game.getCurrentNightNumber();
        NightAction action = new NightAction(actorId, null, NightActionType.SKIP, nightNumber, extractComment(payload));

        return recordAction(game, action, "😴 Sheriff skipped their turn.");
    }

    @PostMapping("/dig/{gameId}/{userId}")
    public ResponseEntity<?> dig(
            @PathVariable Long gameId,
            @PathVariable Long userId,
            @RequestBody DigRequest request
    ) {
        if (request == null || request.amount() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dig amount is required."));
        }

        UUID sessionId = getUuid(gameId);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found for game ID " + gameId));
        }

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Game not found"));
        }

        try {
            DigResponse response = digService.dig(game, userId, request.amount());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/voice/{gameId}/{userId}")
    public ResponseEntity<?> voice(
            @PathVariable Long gameId,
            @PathVariable Long userId,
            @RequestBody VoiceRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Voice message is required."));
        }

        UUID sessionId = getUuid(gameId);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found for game ID " + gameId));
        }

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Game not found"));
        }

        try {
            VoiceResponse response = voiceService.voice(game, userId, request.message());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns available actions and targets for this player during the night.
     */
    @GetMapping("/{gameId}/{userId}/actions")
    public ResponseEntity<NightActionCatalogDTO> getAvailableActions(
            @PathVariable Long gameId,
            @PathVariable Long userId
    ) {
        GameSessionRuntime game = gameManagerService.findSessionById(gameId)
                .orElseThrow(() -> new IllegalStateException("Game not found"));
        PlayerInGame player = game.findPlayerById(userId)
                .orElseThrow(() -> new IllegalStateException("Player not in game"));

        NightActionCatalogDTO catalog = actionService.computeActionsFor(game, player);
        return ResponseEntity.ok(catalog);
    }

    /**
     * Executes a player's chosen night action.
     * (Temporary stub for now — we’ll wire full logic later.)
     */
    @PostMapping("/{gameId}/{userId}/act")
    public ResponseEntity<String> performNightAction(
            @PathVariable Long gameId,
            @PathVariable Long userId,
            @RequestBody NightActionRequest request
    ) {
        // Placeholder — for now, just echo the choice
        String msg = "🐷 Action received: " + request.actionCode() +
                " -> targetUserId=" + request.targetUserId() +
                ", targetRole=" + request.targetRole();

        System.out.println("Night action received: " + msg);
        return ResponseEntity.ok(msg);
    }

    private String extractComment(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("comment")) {
            return null;
        }

        Object rawComment = payload.get("comment");
        return rawComment == null ? null : rawComment.toString();
    }

    private ResponseEntity<String> recordAction(GameSessionRuntime game, NightAction action, String successMessage) {
        try {
            actionService.submitNightAction(game, action);
            return ResponseEntity.ok(successMessage);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
