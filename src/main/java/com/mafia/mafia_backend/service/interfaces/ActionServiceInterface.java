package com.mafia.mafia_backend.service.interfaces;

import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;

import java.util.List;

public interface ActionServiceInterface {

    /**
     * Submit or replace an action for the given actor.
     * If the actor already has an action this night, it will be overwritten.
     */
    void submitNightAction(GameSessionRuntime game, NightAction action);

    /**
     * Cancel the actor's action for the current night.
     */
    void cancelNightAction(GameSessionRuntime game, Long actorId, int nightNumber);

    /**
     * Get all actions submitted for the given night.
     */
    List<NightAction> getNightActions(GameSessionRuntime game, int nightNumber);

    /**
     * Check if all required players for this night have acted.
     */
    boolean allNightActionsComplete(GameSessionRuntime game, int nightNumber);

    /**
     * Resolve the collected actions at the end of the night,
     * applying their effects and producing results for DAY_RESULTS phase.
     */
    void resolveNightActions(GameSessionRuntime game, int nightNumber);

    NightActionCatalogDTO computeActionsFor(GameSessionRuntime game, PlayerInGame player);
}
