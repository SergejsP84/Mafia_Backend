package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.enums.SurvivalBonusType;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FourPlayerGameSimulationTest {

    private ActionService actionService;
    private PrivateMessagingService privateMessagingService;

    @BeforeEach
    void setUp() {
        ConfigSettingService configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        GameManagerService gameManagerService = mock(GameManagerService.class);
        doAnswer(invocation -> {
            GameSessionRuntime game = invocation.getArgument(0);
            PlayerInGame victim = invocation.getArgument(1);
            victim.setAlive(false);
            if ("Mafia".equalsIgnoreCase(victim.getRole().getRoleName())) {
                @SuppressWarnings("unchecked")
                List<Long> mafiaOrder = (List<Long>) game.getStageData().get("mafiaOrder");
                if (mafiaOrder != null) {
                    mafiaOrder.remove(victim.getUser().getId());
                }
            }
            return null;
        }).when(gameManagerService).handlePlayerDeath(any(), any(), anyLong());

        privateMessagingService = new PrivateMessagingService();
        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "gameManagerService", gameManagerService);
        ReflectionTestUtils.setField(actionService, "privateMessagingService", privateMessagingService);
        ReflectionTestUtils.setField(actionService, "gameEconomyService", new GameEconomyService());
    }

    @Test
    void sheriffAndMafiaBothScoreWhenTheyTargetSamePlayer() {
        Simulation simulation = newSimulation();

        submit(simulation, simulation.sheriff, simulation.townsfolkOne, NightActionType.KILL);
        submit(simulation, simulation.mafia, simulation.townsfolkOne, NightActionType.KILL);

        actionService.resolveNightActions(simulation.game, 1);

        assertFalse(simulation.townsfolkOne.isAlive());
        assertTrue(simulation.sheriff.isAlive());
        assertTrue(simulation.mafia.isAlive());
        assertEquals(-19, simulation.sheriff.getInGameMoney());
        assertEquals(11, simulation.mafia.getInGameMoney());
        assertEquals(GamePhase.DAY_VOTING, simulation.game.getStage());
    }

    @Test
    void sheriffAndMafiaCanKillEachOtherInSameNight() {
        Simulation simulation = newSimulation();

        submit(simulation, simulation.sheriff, simulation.mafia, NightActionType.KILL);
        submit(simulation, simulation.mafia, simulation.sheriff, NightActionType.KILL);

        actionService.resolveNightActions(simulation.game, 1);

        assertFalse(simulation.sheriff.isAlive());
        assertFalse(simulation.mafia.isAlive());
        assertEquals(25, simulation.sheriff.getInGameMoney());
        assertEquals(25, simulation.mafia.getInGameMoney());
        assertEquals(GamePhase.ENDED, simulation.game.getStage());
    }

    @Test
    void victoryIsEvaluatedAfterAllCommittedNightActionsResolve() {
        Simulation simulation = newSimulation();

        submit(simulation, simulation.sheriff, simulation.mafia, NightActionType.KILL);
        submit(simulation, simulation.mafia, simulation.townsfolkOne, NightActionType.KILL);

        actionService.resolveNightActions(simulation.game, 1);

        assertFalse(simulation.mafia.isAlive());
        assertFalse(simulation.townsfolkOne.isAlive());
        assertTrue(simulation.sheriff.isAlive());
        assertEquals(GamePhase.ENDED, simulation.game.getStage());
    }

    @Test
    void explicitSkipsPenalizeAllNightActors() {
        Simulation simulation = newSimulation();

        submit(simulation, simulation.sheriff, null, NightActionType.SKIP);
        submit(simulation, simulation.mafia, null, NightActionType.SKIP);

        actionService.resolveNightActions(simulation.game, 1);

        assertEquals(-4, simulation.sheriff.getInGameMoney());
        assertEquals(-4, simulation.mafia.getInGameMoney());
        assertEquals(GamePhase.DAY_VOTING, simulation.game.getStage());
    }

    @Test
    void sheriffCheckSendsPrivateRoleResultToSheriff() {
        Simulation simulation = newSimulation();

        submit(simulation, simulation.sheriff, simulation.mafia, NightActionType.CHECK);
        submit(simulation, simulation.mafia, null, NightActionType.SKIP);

        actionService.resolveNightActions(simulation.game, 1);

        List<String> sheriffMessages = privateMessagingService.getMessagesForPlayer(
                simulation.sheriff.getUser().getId()
        );

        assertTrue(sheriffMessages.stream()
                .anyMatch(message -> message.contains("MafiaPlayer") && message.contains("Mafia")));
        assertEquals(16, simulation.sheriff.getInGameMoney());
        assertEquals(GamePhase.DAY_VOTING, simulation.game.getStage());
    }

    @Test
    void sixteenPlayerReferenceAmountsRemainUnchangedForNightActions() {
        Simulation simulation = newSimulation(16);

        submit(simulation, simulation.sheriff, simulation.mafia, NightActionType.CHECK);
        submit(simulation, simulation.mafia, simulation.townsfolkOne, NightActionType.KILL);

        actionService.resolveNightActions(simulation.game, 1);

        assertEquals(33, simulation.sheriff.getInGameMoney());
        assertEquals(23, simulation.mafia.getInGameMoney());

        simulation = newSimulation(16);
        submit(simulation, simulation.sheriff, simulation.townsfolkOne, NightActionType.KILL);
        submit(simulation, simulation.mafia, null, NightActionType.SKIP);

        actionService.resolveNightActions(simulation.game, 1);

        assertEquals(-37, simulation.sheriff.getInGameMoney());
    }

    @TestFactory
    Collection<DynamicTest> allImplementedNightActionPairsResolveAsExpected() {
        List<ActionChoice> sheriffChoices = List.of(
                check(Target.MAFIA),
                check(Target.SHERIFF),
                check(Target.TOWN_ONE),
                check(Target.TOWN_TWO),
                kill(Target.MAFIA),
                kill(Target.SHERIFF),
                kill(Target.TOWN_ONE),
                kill(Target.TOWN_TWO),
                skip()
        );
        List<ActionChoice> mafiaChoices = List.of(
                kill(Target.MAFIA),
                kill(Target.SHERIFF),
                kill(Target.TOWN_ONE),
                kill(Target.TOWN_TWO),
                skip()
        );

        List<DynamicTest> tests = new ArrayList<>();
        for (ActionChoice sheriffChoice : sheriffChoices) {
            for (ActionChoice mafiaChoice : mafiaChoices) {
                String name = "Sheriff " + sheriffChoice + ", Mafia " + mafiaChoice;
                tests.add(DynamicTest.dynamicTest(name, () -> {
                    Simulation simulation = newSimulation();
                    submit(simulation, simulation.sheriff, playerFor(simulation, sheriffChoice.target()), sheriffChoice.type());
                    submit(simulation, simulation.mafia, playerFor(simulation, mafiaChoice.target()), mafiaChoice.type());

                    actionService.resolveNightActions(simulation.game, 1);

                    assertEquals(expectedSheriffMoney(simulation, sheriffChoice), simulation.sheriff.getInGameMoney());
                    assertEquals(expectedMafiaMoney(simulation, mafiaChoice), simulation.mafia.getInGameMoney());
                    assertAliveStates(simulation, sheriffChoice, mafiaChoice);
                    assertEquals(expectedPhase(simulation), simulation.game.getStage());
                }));
            }
        }
        return tests;
    }

    private void submit(Simulation simulation,
                        PlayerInGame actor,
                        PlayerInGame target,
                        NightActionType actionType) {
        actionService.submitNightAction(
                simulation.game,
                new NightAction(
                        actor.getUser().getId(),
                        target == null ? null : target.getUser().getId(),
                        actionType,
                        1
                )
        );
    }

    private ActionChoice check(Target target) {
        return new ActionChoice(NightActionType.CHECK, target);
    }

    private ActionChoice kill(Target target) {
        return new ActionChoice(NightActionType.KILL, target);
    }

    private ActionChoice skip() {
        return new ActionChoice(NightActionType.SKIP, Target.NONE);
    }

    private PlayerInGame playerFor(Simulation simulation, Target target) {
        return switch (target) {
            case MAFIA -> simulation.mafia;
            case SHERIFF -> simulation.sheriff;
            case TOWN_ONE -> simulation.townsfolkOne;
            case TOWN_TWO -> simulation.townsfolkTwo;
            case NONE -> null;
        };
    }

    private long expectedSheriffMoney(Simulation simulation, ActionChoice sheriffChoice) {
        long baseMoney;
        if (sheriffChoice.type() == NightActionType.SKIP) {
            baseMoney = -10;
        } else if (sheriffChoice.type() == NightActionType.CHECK) {
            baseMoney = switch (sheriffChoice.target()) {
                case MAFIA -> 30;
                case TOWN_ONE, TOWN_TWO -> 10;
                case SHERIFF, NONE -> 0;
            };
        } else {
            baseMoney = switch (sheriffChoice.target()) {
                case MAFIA -> 50;
                case TOWN_ONE, TOWN_TWO -> -40;
                case SHERIFF, NONE -> 0;
            };
        }
        return scaled(baseMoney)
                + nightSurvivalBonusFor(simulation.game, simulation.sheriff)
                + drawBonusFor(simulation.game, simulation.sheriff);
    }

    private long expectedMafiaMoney(Simulation simulation, ActionChoice mafiaChoice) {
        long baseMoney;
        if (mafiaChoice.type() == NightActionType.SKIP) {
            baseMoney = -10;
        } else {
            baseMoney = switch (mafiaChoice.target()) {
                case SHERIFF -> 50;
                case TOWN_ONE, TOWN_TWO -> 20;
                case MAFIA, NONE -> 0;
            };
        }
        return scaled(baseMoney)
                + nightSurvivalBonusFor(simulation.game, simulation.mafia)
                + drawBonusFor(simulation.game, simulation.mafia);
    }

    private long nightSurvivalBonusFor(GameSessionRuntime game, PlayerInGame player) {
        return player.isAlive() ? new GameEconomyService()
                .scaleSurvivalBonusAmount(game, SurvivalBonusType.NIGHT, 3) : 0L;
    }

    private long drawBonusFor(GameSessionRuntime game, PlayerInGame player) {
        return new VictoryService().evaluate(game)
                .filter(rule -> rule.getWinner() == Alignment.NONE && rule.isDraw())
                .filter(rule -> player.isAlive())
                .map(rule -> scaled(25))
                .orElse(0L);
    }

    private long scaled(long baseMoney) {
        return Math.round(baseMoney * Math.sqrt(4 / 16.0));
    }

    private void assertAliveStates(Simulation simulation, ActionChoice sheriffChoice, ActionChoice mafiaChoice) {
        assertEquals(expectedAlive(Target.MAFIA, sheriffChoice, mafiaChoice), simulation.mafia.isAlive());
        assertEquals(expectedAlive(Target.SHERIFF, sheriffChoice, mafiaChoice), simulation.sheriff.isAlive());
        assertEquals(expectedAlive(Target.TOWN_ONE, sheriffChoice, mafiaChoice), simulation.townsfolkOne.isAlive());
        assertEquals(expectedAlive(Target.TOWN_TWO, sheriffChoice, mafiaChoice), simulation.townsfolkTwo.isAlive());
    }

    private boolean expectedAlive(Target player, ActionChoice sheriffChoice, ActionChoice mafiaChoice) {
        boolean killedBySheriff = sheriffChoice.type() == NightActionType.KILL && sheriffChoice.target() == player;
        boolean killedByMafia = mafiaChoice.type() == NightActionType.KILL && mafiaChoice.target() == player;
        return !killedBySheriff && !killedByMafia;
    }

    private GamePhase expectedPhase(Simulation simulation) {
        return new VictoryService().evaluate(simulation.game).isPresent()
                ? GamePhase.ENDED
                : GamePhase.DAY_VOTING;
    }

    private Simulation newSimulation() {
        return newSimulation(4);
    }

    private Simulation newSimulation(int initialPlayerCount) {
        Role mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        Role sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        Role townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");

        PlayerInGame mafia = player(1L, "MafiaPlayer", mafiaRole);
        PlayerInGame sheriff = player(2L, "SheriffPlayer", sheriffRole);
        PlayerInGame townsfolkOne = player(3L, "TownOne", townsfolkRole);
        PlayerInGame townsfolkTwo = player(4L, "TownTwo", townsfolkRole);

        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(initialPlayerCount);
        game.setPlayers(new ArrayList<>(List.of(mafia, sheriff, townsfolkOne, townsfolkTwo)));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(mafia.getUser().getId())));
        game.getStageData().put("currentMafiaIndex", 0);

        return new Simulation(game, mafia, sheriff, townsfolkOne, townsfolkTwo);
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

    private record Simulation(
            GameSessionRuntime game,
            PlayerInGame mafia,
            PlayerInGame sheriff,
            PlayerInGame townsfolkOne,
            PlayerInGame townsfolkTwo
    ) {
    }

    private record ActionChoice(NightActionType type, Target target) {
    }

    private enum Target {
        MAFIA,
        SHERIFF,
        TOWN_ONE,
        TOWN_TWO,
        NONE
    }
}
