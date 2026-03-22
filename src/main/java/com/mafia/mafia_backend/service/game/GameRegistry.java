package com.mafia.mafia_backend.service.game;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.PlayerInGameEntity;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.service.implementation.PrivateMessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRegistry {

    @Autowired
    private PrivateMessagingService privateMessagingService;
    private final Map<Long, Game> activeGames = new ConcurrentHashMap<>();

    public void registerGame(Game game) {
        if (game != null && game.getId() != null) {
            activeGames.put(game.getId(), game);
            System.out.println("🐽 Registered game " + game.getId() + " in registry");
        }
    }

    public Game getGame(Long id) {
        return activeGames.get(id);
    }

    public void removeGame(Long id) {
        Game removed = activeGames.remove(id);
        if (removed != null) {
            System.out.println("💀 Game " + id + " removed from registry (ENDED).");
        }
    }

    public Game createGame() {
        Game newGame = new Game();
        activeGames.put(newGame.getId(), newGame);
        return newGame;
    }

    public Map<Long, Game> getAllGames() {
        return activeGames;
    }

    public boolean joinGame(Game game, User user) {
        if (game == null || game.getPhase() != GamePhase.LOBBY) {
            return false; // Only allow joins in lobby phase
        }
        privateMessagingService.clearMessages(user.getId());

        // Check if user already joined
        boolean alreadyJoined = game.getPlayers().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId()));
        if (alreadyJoined) {
            return false;
        }

        // Create and add new PlayerInGame
        PlayerInGameEntity newPlayer = new PlayerInGameEntity(user, true);
        return true;
    }
}