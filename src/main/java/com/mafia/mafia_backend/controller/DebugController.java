package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final GameManagerService gameManagerService;

    @PostMapping("/force-stage/by-game/{gameId}/{phase}")
    public ResponseEntity<String> forceStageByGame(@PathVariable Long gameId,
                                                   @PathVariable String phase) {
        GameSessionRuntime game = gameManagerService.findByGameId(gameId);
        if (game == null) return ResponseEntity.notFound().build();
        try {
            GamePhase newPhase = GamePhase.valueOf(phase.toUpperCase());
            game.advanceStage(newPhase);
            return ResponseEntity.ok("Forced stage of game " + gameId + " to " + newPhase);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid phase name");
        }
    }

    @GetMapping("/state/{sessionId}")
    public ResponseEntity<?> getGameState(@PathVariable UUID sessionId) {
        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.notFound().build();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stage", game.getStage());
        info.put("alivePlayers", game.getPlayers().stream().filter(PlayerInGame::isAlive)
                .map(p -> p.getUser().getUsername()).toList());
        info.put("votes", game.getAllVotes());
        info.put("verdictVotes", game.getVerdictVotes());
//        info.put("contracts", game.getContractOrders());
        info.put("logs", game.getLog());
        return ResponseEntity.ok(info);
    }

    @DeleteMapping("/reset")
    public ResponseEntity<String> resetAllGames() {
        int count = gameManagerService.clearAllGames();
        return ResponseEntity.ok("Cleared " + count + " active sessions.");
    }

    @GetMapping("/list")
    public ResponseEntity<List<NightAction>> listActions(@RequestParam UUID sessionId,
                                                         @RequestParam int nightNumber) {
        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(game.getActionsForNight(nightNumber));
    }

    @GetMapping("/{sessionId}/mafia-order")
    public ResponseEntity<Map<String, Object>> getMafiaOrder(@PathVariable UUID sessionId) {
        Optional<GameSessionRuntime> gameOpt = gameManagerService.findSessionByUuid(sessionId);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Game not found"));
        }

        GameSessionRuntime game = gameOpt.get();
        Map<String, Object> stageData = game.getStageData();

        List<Long> mafiaOrder = (List<Long>) stageData.get("mafiaOrder");
        Integer currentIndex = (Integer) stageData.get("currentMafiaIndex");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mafiaOrder", mafiaOrder != null ? mafiaOrder : List.of());
        result.put("currentMafiaIndex", currentIndex);
        result.put("currentMafiaPlayer",
                (mafiaOrder != null && currentIndex != null && currentIndex < mafiaOrder.size())
                        ? mafiaOrder.get(currentIndex)
                        : null);

        return ResponseEntity.ok(result);
    }

}