package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.service.implementation.GameEconomyService;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DebugControllerTest {

    private GameManagerService gameManagerService;
    private DebugController debugController;
    private GameSessionRuntime game;
    private PlayerInGame testPiglet;
    private PlayerInGame unrelatedPiglet;

    @BeforeEach
    void setUp() {
        gameManagerService = mock(GameManagerService.class);
        debugController = new DebugController(gameManagerService, new GameEconomyService());

        game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));

        testPiglet = player(3L, "TestPiglet3");
        unrelatedPiglet = player(4L, "TestPiglet4");
        unrelatedPiglet.setInGameMoney(140);
        unrelatedPiglet.setTier(3);
        game.getPlayers().add(testPiglet);
        game.getPlayers().add(unrelatedPiglet);

        when(gameManagerService.findByGameId(game.getGame().getId())).thenReturn(game);
    }

    @Test
    void setMoneyBelowTierTwoThresholdKeepsTierOne() {
        DebugController.DebugMoneyResponse response = setMoney(59);

        assertEquals("TestPiglet3", response.player());
        assertEquals(3L, response.userId());
        assertEquals(59, response.money());
        assertEquals(1, response.tier());
        assertEquals(1, testPiglet.getTier());
    }

    @Test
    void setMoneyExactlyAtThresholdRecalculatesToHigherTier() {
        DebugController.DebugMoneyResponse response = setMoney(60);

        assertEquals(60, response.money());
        assertEquals(2, response.tier());
        assertEquals(2, testPiglet.getTier());
    }

    @Test
    void setMoneyCanJumpDirectlyFromTierOneToTierFour() {
        DebugController.DebugMoneyResponse response = setMoney(300);

        assertEquals(300, response.money());
        assertEquals(4, response.tier());
        assertEquals(4, testPiglet.getTier());
    }

    @Test
    void setMoneyCanLowerTierFourPlayerBackToTierOne() {
        setMoney(300);

        DebugController.DebugMoneyResponse response = setMoney(10);

        assertEquals(10, response.money());
        assertEquals(1, response.tier());
        assertEquals(1, testPiglet.getTier());
    }

    @Test
    void setMoneyAllowsNegativeDebugMoney() {
        DebugController.DebugMoneyResponse response = setMoney(-25);

        assertEquals(-25, response.money());
        assertEquals(1, response.tier());
        assertEquals(1, testPiglet.getTier());
    }

    @Test
    void setMoneyDoesNotModifyUnrelatedPlayers() {
        setMoney(300);

        assertEquals(140, unrelatedPiglet.getInGameMoney());
        assertEquals(3, unrelatedPiglet.getTier());
    }

    private DebugController.DebugMoneyResponse setMoney(long amount) {
        ResponseEntity<?> response = debugController.setPlayerMoney(
                game.getGame().getId(),
                testPiglet.getUser().getId(),
                amount
        );

        assertEquals(200, response.getStatusCode().value());
        return assertInstanceOf(DebugController.DebugMoneyResponse.class, response.getBody());
    }

    private PlayerInGame player(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setAlive(true);
        player.setTier(1);
        player.setInGameMoney(0);
        return player;
    }
}
