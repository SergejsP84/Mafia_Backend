package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/defense")
@RequiredArgsConstructor
public class DefenseController {

    private final GameManagerService gameManagerService;

    @PostMapping("/submit")
    public ResponseEntity<String> submitDefense(@RequestParam UUID sessionId,
                                                @RequestParam Long playerId,
                                                @RequestParam String text) {
        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.notFound().build();

        game.addPublicMessage("🗣 " + game.findPlayerById(playerId).map(p -> p.getUser().getUsername()).orElse("Unknown") +
                " shouts from the gallows: " + text);
        game.addLog("Defense text submitted: " + text);
        return ResponseEntity.ok("Defense recorded.");
    }

    /**
     * Allows the accused player to end their defense speech early.
     */
    @PostMapping("/end")
    public ResponseEntity<String> endDefenseEarly(@RequestParam UUID sessionId,
                                                  @RequestParam Long accusedId) {

        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.badRequest().body("❌ Game not found");

        Map<String, Object> stageData = game.getStageData();

        // Verify that the current accused matches the request
        Long currentAccusedId = game.getAccusedUserId();
        if (currentAccusedId == null || !currentAccusedId.equals(accusedId)) {
            return ResponseEntity.badRequest().body("🚫 You are not the current accused player.");
        }

        // Check current phase
        if (game.getStage() != GamePhase.HANGING_DEFENSE) {
            return ResponseEntity.badRequest().body("⚠️ You can only end defense during the defense phase.");
        }

        // Mark flag
        stageData.put("defenseEndedEarly", true);
        game.addLog("🎙 Accused player " + accusedId + " ended defense early.");
        game.addPublicMessage("🎙 The accused has finished their defense.");

        return ResponseEntity.ok("✅ Defense ended early. Proceeding soon to verdict voting.");
    }
}

