package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.controller.ActionController;
import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.process.GamePhaseScheduler;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.game.GameRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MafiaRotationTest {

    private ActionService actionService;
    private GameManagerService gameManagerService;
    private ConfigSettingService configSettingService;

    @BeforeEach
    void setUp() {
        configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "gameEconomyService", new GameEconomyService());

        gameManagerService = new GameManagerService(
                mock(ConfigSettingRepository.class),
                mock(RoleRepository.class),
                mock(UserService.class),
                configSettingService,
                mock(RoleRefusalTrackerRepository.class),
                mock(GameRegistry.class)
        );
        ReflectionTestUtils.setField(gameManagerService, "privateMessagingService", new PrivateMessagingService());
        ReflectionTestUtils.setField(actionService, "gameManagerService", gameManagerService);
        ReflectionTestUtils.setField(actionService, "privateMessagingService", new PrivateMessagingService());
    }

    @Test
    void onlyOneMafiaIsActiveOnNightOne() {
        MultiMafiaGame simulation = newMultiMafiaGame();

        assertTrue(actionService.isActiveMafia(simulation.game(), simulation.mafiaOne().getUser().getId()));
        assertFalse(actionService.isActiveMafia(simulation.game(), simulation.mafiaTwo().getUser().getId()));

        NightActionCatalogDTO activeCatalog = actionService.computeActionsFor(simulation.game(), simulation.mafiaOne());
        NightActionCatalogDTO inactiveCatalog = actionService.computeActionsFor(simulation.game(), simulation.mafiaTwo());

        assertEquals(1, activeCatalog.actions().size());
        assertEquals("KILL", activeCatalog.actions().get(0).code());
        assertTrue(inactiveCatalog.actions().isEmpty());
    }

    @Test
    void inactiveMafiaCannotSubmitThroughMafiaEndpoint() {
        MultiMafiaGame simulation = newMultiMafiaGame();
        gameManagerService.getActiveGames().add(simulation.game());
        ActionController controller = new ActionController(actionService, gameManagerService, mock(DigService.class));

        ResponseEntity<String> response = controller.mafiaKill(
                simulation.game().getGame().getId(),
                simulation.mafiaTwo().getUser().getId(),
                Map.of("targetUserId", simulation.town().getUser().getId())
        );

        assertEquals(403, response.getStatusCode().value());
        assertTrue(simulation.game().getActionsForNight(1).isEmpty());
        assertFalse(simulation.mafiaTwo().isHasActedTonight());
    }

    @Test
    void inactiveMafiaDoesNotBlockNightCompletion() {
        MultiMafiaGame simulation = newMultiMafiaGame();
        simulation.mafiaOne().setHasActedTonight(true);
        simulation.sheriff().setHasActedTonight(true);

        assertTrue(gameManagerService.allNightActionsComplete(simulation.game()));
    }

    @Test
    void rotationAdvancesToNextMafiaOnNextNight() {
        MultiMafiaGame simulation = newMultiMafiaGame();
        GamePhaseScheduler scheduler = newScheduler();

        ReflectionTestUtils.invokeMethod(scheduler, "advanceMafiaRotationIfNeeded", simulation.game());

        assertFalse(actionService.isActiveMafia(simulation.game(), simulation.mafiaOne().getUser().getId()));
        assertTrue(actionService.isActiveMafia(simulation.game(), simulation.mafiaTwo().getUser().getId()));
        assertTrue(actionService.computeActionsFor(simulation.game(), simulation.mafiaOne()).actions().isEmpty());
        assertEquals(1, actionService.computeActionsFor(simulation.game(), simulation.mafiaTwo()).actions().size());
    }

    @Test
    void afterActiveMafiaDiesRemainingMafiaRotationStillWorks() {
        MultiMafiaGame simulation = newMultiMafiaGame();

        gameManagerService.handlePlayerDeath(simulation.game(), simulation.mafiaOne(), simulation.sheriff().getUser().getId());
        simulation.mafiaTwo().setHasActedTonight(true);
        simulation.sheriff().setHasActedTonight(true);

        assertFalse(simulation.mafiaOne().isAlive());
        assertTrue(actionService.isActiveMafia(simulation.game(), simulation.mafiaTwo().getUser().getId()));
        assertEquals(List.of(simulation.mafiaTwo().getUser().getId()), simulation.game().getStageData().get("mafiaOrder"));
        assertTrue(gameManagerService.allNightActionsComplete(simulation.game()));
    }

    @Test
    void fourPlayerSingleMafiaBehaviorRemainsUnchanged() {
        MultiMafiaGame simulation = newSingleMafiaGame();

        assertTrue(actionService.isActiveMafia(simulation.game(), simulation.mafiaOne().getUser().getId()));
        assertEquals(1, actionService.computeActionsFor(simulation.game(), simulation.mafiaOne()).actions().size());
        assertFalse(actionService.allNightActionsComplete(simulation.game(), 1));

        actionService.submitNightAction(simulation.game(), new NightAction(
                simulation.mafiaOne().getUser().getId(),
                simulation.town().getUser().getId(),
                NightActionType.KILL,
                1
        ));

        assertTrue(actionService.allNightActionsComplete(simulation.game(), 1));
    }

    private GamePhaseScheduler newScheduler() {
        return new GamePhaseScheduler(
                gameManagerService,
                mock(RoleRepository.class),
                mock(UserService.class),
                configSettingService,
                new VictoryService(),
                actionService,
                new PrivateMessagingService(),
                new GameEconomyService()
        );
    }

    private MultiMafiaGame newMultiMafiaGame() {
        Role mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        Role sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        Role townRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");

        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame sheriff = player(3L, "Sheriff", sheriffRole);
        PlayerInGame town = player(4L, "Town", townRole);

        GameSessionRuntime game = game(List.of(mafiaOne, mafiaTwo, sheriff, town));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(
                mafiaOne.getUser().getId(),
                mafiaTwo.getUser().getId()
        )));
        game.getStageData().put("currentMafiaIndex", 0);

        return new MultiMafiaGame(game, mafiaOne, mafiaTwo, sheriff, town);
    }

    private MultiMafiaGame newSingleMafiaGame() {
        Role mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        Role townRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");

        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame town = player(2L, "Town", townRole);

        GameSessionRuntime game = game(List.of(mafia, town));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(mafia.getUser().getId())));
        game.getStageData().put("currentMafiaIndex", 0);

        return new MultiMafiaGame(game, mafia, null, null, town);
    }

    private GameSessionRuntime game(List<PlayerInGame> players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setPlayers(new ArrayList<>(players));
        return game;
    }

    private PlayerInGame player(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        player.setAlive(true);
        return player;
    }

    private record MultiMafiaGame(
            GameSessionRuntime game,
            PlayerInGame mafiaOne,
            PlayerInGame mafiaTwo,
            PlayerInGame sheriff,
            PlayerInGame town
    ) {
    }
}
