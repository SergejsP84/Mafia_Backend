package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSessionRuntimeTest {

    @Test
    void advanceStageKeepsGamePhaseInSync() {
        GameSessionRuntime runtime = new GameSessionRuntime(null);
        Game game = new Game();
        runtime.setGame(game);

        runtime.advanceStage(GamePhase.NIGHT);

        assertEquals(GamePhase.NIGHT, runtime.getStage());
        assertEquals(GamePhase.NIGHT, game.getPhase());
    }

    @Test
    void attachingGameCopiesCurrentRuntimeStage() {
        GameSessionRuntime runtime = new GameSessionRuntime(null);
        runtime.advanceStage(GamePhase.DAY_VOTING);
        Game game = new Game();

        runtime.setGame(game);

        assertEquals(GamePhase.DAY_VOTING, game.getPhase());
    }

    @Test
    void generatedStyleStageSetterAlsoKeepsGamePhaseInSync() {
        GameSessionRuntime runtime = new GameSessionRuntime(null);
        Game game = new Game();
        runtime.setGame(game);

        runtime.setStage(GamePhase.CONTRACTS);

        assertEquals(GamePhase.CONTRACTS, runtime.getStage());
        assertEquals(GamePhase.CONTRACTS, game.getPhase());
    }
}
