package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MafiaMoneyDistributionTest {

    private ActionService actionService;
    private GameEconomyService economyService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;
    private Role lawyerRole;

    @BeforeEach
    void setUp() {
        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
        lawyerRole = new Role(4L, "Lawyer", Alignment.MAFIA, false, false, false, "Future support role");

        ConfigSettingService configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        GameManagerService gameManagerService = mock(GameManagerService.class);
        doAnswer(invocation -> {
            GameSessionRuntime game = invocation.getArgument(0);
            PlayerInGame victim = invocation.getArgument(1);
            victim.setAlive(false);
            if (victim.getRole() != null && victim.getRole().getRoleName().equalsIgnoreCase("mafia")) {
                @SuppressWarnings("unchecked")
                List<Long> mafiaOrder = (List<Long>) game.getStageData().get("mafiaOrder");
                if (mafiaOrder != null) {
                    mafiaOrder.remove(victim.getUser().getId());
                }
            }
            return null;
        }).when(gameManagerService).handlePlayerDeath(any(), any(), anyLong());

        economyService = new GameEconomyService();
        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "gameManagerService", gameManagerService);
        ReflectionTestUtils.setField(actionService, "privateMessagingService", new PrivateMessagingService());
        ReflectionTestUtils.setField(actionService, "gameEconomyService", economyService);
    }

    @Test
    void singleMafiaKillRewardRemainsEquivalentToPersonalReward() {
        PlayerInGame mafia = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame sheriff = player(2L, "Sheriff", sheriffRole);
        PlayerInGame target = player(3L, "TownTarget", townsfolkRole);
        PlayerInGame town = player(4L, "Town", townsfolkRole);
        GameSessionRuntime game = game(4, List.of(mafia, sheriff, target, town));

        submit(game, sheriff, sheriff, NightActionType.CHECK);
        submit(game, mafia, target, NightActionType.KILL);

        actionService.resolveNightActions(game, 1);

        assertEquals(11, mafia.getInGameMoney());
    }

    @Test
    void successfulMafiaKillRewardIsSharedWithEveryLivingOrdinaryMafiaAndNotDivided() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame mafiaThree = player(3L, "MafiaThree", mafiaRole);
        PlayerInGame deadMafia = player(4L, "DeadMafia", mafiaRole);
        deadMafia.setAlive(false);
        PlayerInGame lawyer = player(5L, "Lawyer", lawyerRole);
        PlayerInGame sheriff = player(6L, "Sheriff", sheriffRole);
        PlayerInGame target = player(7L, "TownTarget", townsfolkRole);
        GameSessionRuntime game = game(9, List.of(mafiaOne, mafiaTwo, mafiaThree, deadMafia, lawyer, sheriff, target));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(
                mafiaOne.getUser().getId(),
                mafiaTwo.getUser().getId(),
                mafiaThree.getUser().getId()
        )));

        mafiaOne.setInGameMoney(10);
        mafiaTwo.setInGameMoney(30);
        mafiaOne.setTier(1);
        mafiaTwo.setTier(2);
        mafiaThree.setTier(1);

        submit(game, sheriff, sheriff, NightActionType.CHECK);
        submit(game, mafiaOne, target, NightActionType.KILL);

        actionService.resolveNightActions(game, 1);

        assertEquals(27, mafiaOne.getInGameMoney());
        assertEquals(47, mafiaTwo.getInGameMoney());
        assertEquals(17, mafiaThree.getInGameMoney());
        assertEquals(0, deadMafia.getInGameMoney());
        assertEquals(2, lawyer.getInGameMoney());
        assertEquals(2, sheriff.getInGameMoney());
        assertEquals(0, target.getInGameMoney());
        assertEquals(2, mafiaOne.getTier());
        assertEquals(3, mafiaTwo.getTier());
        assertEquals(1, mafiaThree.getTier());
    }

    @Test
    void nightSurvivalBonusAdvancesTierAndIsAwardedOnlyOncePerNight() {
        PlayerInGame mafia = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame sheriff = player(2L, "Sheriff", sheriffRole);
        PlayerInGame town = player(3L, "Town", townsfolkRole);
        GameSessionRuntime game = game(16, List.of(mafia, sheriff, town));
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));
        sheriff.setInGameMoney(58);
        sheriff.setTier(1);

        submit(game, sheriff, sheriff, NightActionType.CHECK);
        submit(game, mafia, null, NightActionType.SKIP);

        actionService.resolveNightActions(game, 1);
        actionService.resolveNightActions(game, 1);

        assertEquals(61, sheriff.getInGameMoney());
        assertEquals(2, sheriff.getTier());
        assertEquals(3, town.getInGameMoney());
    }

    @Test
    void mafiaSkipPenaltyRemainsPersonal() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame sheriff = player(3L, "Sheriff", sheriffRole);
        GameSessionRuntime game = game(4, List.of(mafiaOne, mafiaTwo, sheriff));

        submit(game, sheriff, sheriff, NightActionType.CHECK);
        submit(game, mafiaOne, null, NightActionType.SKIP);

        actionService.resolveNightActions(game, 1);

        assertEquals(-4, mafiaOne.getInGameMoney());
        assertEquals(1, mafiaTwo.getInGameMoney());
    }

    @Test
    void directEconomyAdjustmentDoesNotPropagateToOtherMafia() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        GameSessionRuntime game = game(16, List.of(mafiaOne, mafiaTwo));

        economyService.adjustMoney(game, mafiaOne, 33, "personal test");

        assertEquals(33, mafiaOne.getInGameMoney());
        assertEquals(0, mafiaTwo.getInGameMoney());
    }

    @Test
    void mafiaTargetingAnotherMafiaFallbackRemainsPersonalAndNotShared() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame mafiaThree = player(3L, "MafiaThree", mafiaRole);
        PlayerInGame sheriff = player(4L, "Sheriff", sheriffRole);
        GameSessionRuntime game = game(16, List.of(mafiaOne, mafiaTwo, mafiaThree, sheriff));

        submit(game, sheriff, sheriff, NightActionType.CHECK);
        submit(game, mafiaOne, mafiaTwo, NightActionType.KILL);

        actionService.resolveNightActions(game, 1);

        assertEquals(13, mafiaOne.getInGameMoney());
        assertEquals(0, mafiaTwo.getInGameMoney());
        assertEquals(3, mafiaThree.getInGameMoney());
        assertFalse(mafiaTwo.isAlive());
    }

    private void submit(GameSessionRuntime game, PlayerInGame actor, PlayerInGame target, NightActionType actionType) {
        actionService.submitNightAction(
                game,
                new NightAction(
                        actor.getUser().getId(),
                        target == null ? null : target.getUser().getId(),
                        actionType,
                        1
                )
        );
    }

    private GameSessionRuntime game(int initialPlayerCount, List<PlayerInGame> players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(initialPlayerCount);
        game.setPlayers(new ArrayList<>(players));
        game.getStageData().put("mafiaOrder", new ArrayList<>(players.stream()
                .filter(this::isLivingOrdinaryMafia)
                .map(player -> player.getUser().getId())
                .toList()));
        game.getStageData().put("currentMafiaIndex", 0);
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 20,
                "tier3", 40,
                "tier4", 80
        ));
        return game;
    }

    private boolean isLivingOrdinaryMafia(PlayerInGame player) {
        return player.isAlive()
                && player.getRole() != null
                && player.getRole().getRoleName().equalsIgnoreCase("mafia");
    }

    private PlayerInGame player(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setMoney(0L);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        player.setAlive(true);
        return player;
    }
}
