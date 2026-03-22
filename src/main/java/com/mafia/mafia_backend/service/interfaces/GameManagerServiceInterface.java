package com.mafia.mafia_backend.service.interfaces;

import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.GamePhase;

import java.util.List;
import java.util.Optional;

public interface GameManagerServiceInterface {
    GameSessionRuntime startNewGame();
    boolean joinGame(GameSessionRuntime game, User user);
    Optional<GameSessionRuntime> findSessionById(Long gameId);
    void advancePhase(GameSessionRuntime game, GamePhase newPhase);
    Optional<GameSessionRuntime> findAvailableGame();

    int getLobbyDurationFromSettings();

    int getMaxPlayersFromSettings();

    String pickRandomIntroMessage();
    void assignInitialRoles(GameSessionRuntime game);
    void offerRolesToPlayers(GameSessionRuntime game, List<Role> rolesToDistribute);
    String confirmOfferedRole(Long gameId, Long userId);
    void refuseOfferedRole(Long gameId, Long userId);
    void offerRoleToNextAvailablePlayer(GameSessionRuntime game, Role offeredRole);
    void banishIdlePlayers(GameSessionRuntime game);
    void incrementRefusalCount(Long userId, String roleName);
    void assignTownsfolkToRemainingPlayers(GameSessionRuntime game);
    void assignPrivateChatAccess(GameSessionRuntime game);
    void assignTierThresholds(GameSessionRuntime game);
    boolean allNightActionsComplete(GameSessionRuntime game);
}
