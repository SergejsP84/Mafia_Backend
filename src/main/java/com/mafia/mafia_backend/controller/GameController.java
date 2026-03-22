package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.dto.GameStateDTO;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.UserRepository;
import com.mafia.mafia_backend.service.game.GameRegistry;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import com.mafia.mafia_backend.service.implementation.PrivateMessagingService;
import com.mafia.mafia_backend.service.implementation.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final GameRegistry gameRegistry;
    private final UserService userService;
    private final GameManagerService gameManagerService;
    private final PrivateMessagingService privateMessagingService;

    @PostMapping("/create")
    public ResponseEntity<Game> createGame() {
        GameSessionRuntime session = gameManagerService.startNewGame(); // Uses the correct list
        return ResponseEntity.ok(session.getGame()); // Return the Game inside it
    }

    @GetMapping("/{id}")
    public ResponseEntity<Game> getGame(@PathVariable Long id) {
        Game game = gameRegistry.getGame(id);
        if (game == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(game);
    }

    @PostMapping("/{gameId}/{userId}/join")
    public ResponseEntity<String> joinGame(@PathVariable Long gameId, @PathVariable Long userId) {
        Optional<GameSessionRuntime> sessionOpt = gameManagerService.findSessionById(gameId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GameSessionRuntime game = sessionOpt.get();
        User user = userService.getUserById(userId);

        // Before allowing to join any new game:
        boolean alreadyInOther = gameManagerService.getActiveGames().stream()
                .anyMatch(g -> g.getPlayers().stream()
                        .anyMatch(p -> p.getUser().getId().equals(user.getId())));

        if (alreadyInOther) {
            throw new IllegalArgumentException("User is already participating in another game.");
        }

        // 🛑 NEW CHECK — reject joining for inactive games
        if (game.getStage() == GamePhase.CANCELED || game.getStage() == GamePhase.ENDED) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Game is no longer active.");
        }

        if (game.getStage() != GamePhase.LOBBY) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Game is no longer accepting new players.");
        }

        boolean success = gameManagerService.joinGame(game, user);
        privateMessagingService.clearMessages(user.getId());

        if (success) {
            game.addPublicMessage("Player " + user.getUsername() + "joined the game!");
            return ResponseEntity.ok("Player joined the game.");
        } else {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Player already in the game.");
        }
    }

    @PostMapping("/{gameId}/{userId}/leave")
    public ResponseEntity<String> leaveGame(@PathVariable Long gameId, @PathVariable Long userId) {
        Optional<GameSessionRuntime> sessionOpt = gameManagerService.findSessionById(gameId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GameSessionRuntime game = sessionOpt.get();

        if (game.getStage() != GamePhase.LOBBY) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("You can only leave during the lobby phase.");
        }

        boolean removed = gameManagerService.leaveGame(game, userId);

        if (removed) {
            game.addPublicMessage("Player left the game.");
            return ResponseEntity.ok("Player left successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Player was not part of this game.");
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<GamePhase> getGamePhase(@PathVariable Long id) {
        Game game = gameRegistry.getGame(id);
        if (game == null) {
            System.out.println("CANNOT FIND THE GAME PORKLET!");
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(game.getPhase());
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<GameStateDTO> getGameState(@PathVariable UUID id) {
        GameSessionRuntime game = gameManagerService.findGameById(id);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        GameStateDTO dto = new GameStateDTO();
        dto.setSessionId(game.getSessionId());
        dto.setStage(game.getStage());
        dto.setPlayers(
                game.getPlayers().stream()
                        .map(p -> new GameStateDTO.PlayerInfo(
                                p.getUser().getUsername(),
                                p.getRole() != null ? p.getRole().getRoleName() : "Unassigned",
                                p.isAlive(),
                                (int) p.getInGameMoney()
                        ))
                        .toList()
        );
        dto.setPublicMessages(game.getPublicMessages());
        dto.setFinished(game.isFinished());

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/private/{playerId}")
    public ResponseEntity<List<String>> getPrivateMessages(@PathVariable Long playerId) {
        return ResponseEntity.ok(privateMessagingService.getMessagesForPlayer(playerId));
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listGames(
            @RequestParam(required = false) Long userId) {

        List<Map<String, Object>> result = gameManagerService.getActiveGames().stream()
                .filter(session -> {
                    GamePhase phase = session.getGame().getPhase();
                    return phase != GamePhase.CANCELED && phase != GamePhase.ENDED;
                })
                .map(session -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", session.getGame().getId());
                    m.put("phase", session.getStage().name());
                    m.put("playerCount", session.getPlayers().size());
                    m.put("createdAt", session.getGame().getCreatedAt());

                    // 🐷 New part: joined flag
                    boolean joined = false;
                    if (userId != null) {
                        joined = session.getPlayers().stream()
                                .anyMatch(p -> p.getUser().getId().equals(userId));
                    }
                    m.put("joined", joined);

                    return m;
                })
                .toList();

        return ResponseEntity.ok(result);
    }
}