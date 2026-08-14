package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameEconomyServiceTest {

    private GameEconomyService economyService;
    private GameSessionRuntime game;
    private PlayerInGame player;

    @BeforeEach
    void setUp() {
        economyService = new GameEconomyService();
        game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));

        User user = new User();
        user.setId(8L);
        user.setUsername("TestPiglet");

        player = new PlayerInGame();
        player.setUser(user);
        player.setTier(1);
        player.setInGameMoney(0);
    }

    @Test
    void positiveMoneyChangeWithoutTierChange() {
        player.setInGameMoney(10);
        player.setTier(1);

        economyService.adjustMoney(game, player, 20, "test");

        assertEquals(30, player.getInGameMoney());
        assertEquals(1, player.getTier());
    }

    @Test
    void positiveChangeAtThresholdAdvancesOneTier() {
        player.setInGameMoney(50);
        player.setTier(1);

        economyService.adjustMoney(game, player, 10, "test");

        assertEquals(60, player.getInGameMoney());
        assertEquals(2, player.getTier());
    }

    @Test
    void largePositiveChangeCanAdvanceMultipleTiers() {
        economyService.adjustMoney(game, player, 250, "test");

        assertEquals(250, player.getInGameMoney());
        assertEquals(4, player.getTier());
    }

    @Test
    void negativeChangeWithoutTierChange() {
        player.setInGameMoney(100);
        player.setTier(2);

        economyService.adjustMoney(game, player, -20, "test");

        assertEquals(80, player.getInGameMoney());
        assertEquals(2, player.getTier());
    }

    @Test
    void negativeChangeCanDropOneTier() {
        player.setInGameMoney(150);
        player.setTier(3);

        economyService.adjustMoney(game, player, -20, "test");

        assertEquals(130, player.getInGameMoney());
        assertEquals(2, player.getTier());
    }

    @Test
    void largeNegativeChangeCanDropMultipleTiers() {
        player.setInGameMoney(250);
        player.setTier(4);

        economyService.adjustMoney(game, player, -200, "test");

        assertEquals(50, player.getInGameMoney());
        assertEquals(1, player.getTier());
    }

    @Test
    void negativeBalanceRemainsValid() {
        player.setInGameMoney(10);
        player.setTier(1);

        economyService.adjustMoney(game, player, -30, "test");

        assertEquals(-20, player.getInGameMoney());
        assertEquals(1, player.getTier());
    }

    @Test
    void exactThresholdBoundariesUseHigherTier() {
        assertEquals(2, economyService.getTierForMoney(game, 60));
        assertEquals(3, economyService.getTierForMoney(game, 140));
        assertEquals(4, economyService.getTierForMoney(game, 240));
    }

    @Test
    void rewardScalingLeavesSixteenPlayerAmountsUnchanged() {
        game.setInitialPlayerCount(16);

        assertEquals(20, economyService.scaleRewardAmount(game, 20));
        assertEquals(-40, economyService.scaleRewardAmount(game, -40));
        assertEquals(0, economyService.scaleRewardAmount(game, 0));
    }

    @Test
    void rewardScalingHalvesFourPlayerAmounts() {
        game.setInitialPlayerCount(4);

        assertEquals(10, economyService.scaleRewardAmount(game, 20));
        assertEquals(-20, economyService.scaleRewardAmount(game, -40));
    }

    @Test
    void rewardScalingUsesSquareRootCurveForRepresentativePlayerCounts() {
        game.setInitialPlayerCount(9);
        assertEquals(15, economyService.scaleRewardAmount(game, 20));
        assertEquals(-30, economyService.scaleRewardAmount(game, -40));

        game.setInitialPlayerCount(21);
        assertEquals(23, economyService.scaleRewardAmount(game, 20));
        assertEquals(-46, economyService.scaleRewardAmount(game, -40));

        game.setInitialPlayerCount(25);
        assertEquals(25, economyService.scaleRewardAmount(game, 20));
        assertEquals(-50, economyService.scaleRewardAmount(game, -40));

        game.setInitialPlayerCount(40);
        assertEquals(32, economyService.scaleRewardAmount(game, 20));
        assertEquals(-63, economyService.scaleRewardAmount(game, -40));
    }

    @Test
    void rewardScalingUsesMathRoundForFractionalAmounts() {
        game.setInitialPlayerCount(10);

        assertEquals(25, economyService.scaleRewardAmount(game, 31));
        assertEquals(-25, economyService.scaleRewardAmount(game, -31));
    }

    @Test
    void rewardScalingPreservesNonZeroSigns() {
        game.setInitialPlayerCount(4);

        assertEquals(1, economyService.scaleRewardAmount(game, 1));
        assertEquals(-1, economyService.scaleRewardAmount(game, -1));
    }

    @Test
    void rewardScalingUsesInitialPlayerCountAfterPlayersAreRemoved() {
        game.setInitialPlayerCount(16);
        for (long id = 1; id <= 16; id++) {
            User user = new User();
            user.setId(id);
            user.setUsername("TestPiglet" + id);

            PlayerInGame player = new PlayerInGame();
            player.setUser(user);
            game.getPlayers().add(player);
        }

        game.getPlayers().subList(4, 16).clear();

        assertEquals(4, game.getPlayers().size());
        assertEquals(20, economyService.scaleRewardAmount(game, 20));
    }

    @Test
    void rewardScalingRequiresInitialPlayerCount() {
        assertThrows(IllegalStateException.class, () -> economyService.scaleRewardAmount(game, 20));
    }
}
