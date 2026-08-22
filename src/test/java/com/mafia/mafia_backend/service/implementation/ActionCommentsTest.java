package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.controller.ActionController;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionCommentsTest {

    private ActionService actionService;
    private GameManagerService gameManagerService;
    private PrivateMessagingService privateMessagingService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;

    @BeforeEach
    void setUp() {
        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");

        ConfigSettingService configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        gameManagerService = mock(GameManagerService.class);
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

        privateMessagingService = new PrivateMessagingService();
        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "gameManagerService", gameManagerService);
        ReflectionTestUtils.setField(actionService, "privateMessagingService", privateMessagingService);
        ReflectionTestUtils.setField(actionService, "gameEconomyService", new GameEconomyService());
    }

    @Test
    void actionStoresNormalizedCommentAndOldConstructorDefaultsToNoComment() {
        NightAction withComment = new NightAction(1L, 2L, NightActionType.KILL, 1, "  Squee   squeak  ");
        NightAction withoutComment = new NightAction(1L, 2L, NightActionType.KILL, 1);

        assertEquals("Squee   squeak", withComment.getComment());
        assertNull(withoutComment.getComment());
    }

    @Test
    void emptyCommentsNormalizeToNoComment() {
        assertNull(new NightAction(1L, 2L, NightActionType.KILL, 1, null).getComment());
        assertNull(new NightAction(1L, 2L, NightActionType.KILL, 1, "").getComment());
        assertNull(new NightAction(1L, 2L, NightActionType.KILL, 1, "   ").getComment());
    }

    @Test
    void commentLengthLimitAccepts512AndRejects513WithoutStoringAction() {
        GameSessionRuntime game = newSimulation().game();
        String maxComment = "a".repeat(512);
        String tooLong = "a".repeat(513);

        NightAction accepted = new NightAction(1L, 3L, NightActionType.KILL, 1, maxComment);
        actionService.submitNightAction(game, accepted);

        assertEquals(maxComment, game.getActionsForNight(1).get(0).getComment());
        assertThrows(IllegalArgumentException.class,
                () -> new NightAction(1L, 3L, NightActionType.KILL, 1, tooLong));
        assertEquals(1, game.getActionsForNight(1).size());
        assertEquals(maxComment, game.getActionsForNight(1).get(0).getComment());
    }

    @Test
    void replacementReplacesOldActionAndComment() {
        Simulation simulation = newSimulation();

        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.sheriff().getUser().getId(), simulation.townOne().getUser().getId(),
                        NightActionType.KILL, 1, "Got you"));
        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.sheriff().getUser().getId(), simulation.mafia().getUser().getId(),
                        NightActionType.CHECK, 1, "Let's see..."));

        List<NightAction> actions = simulation.game().getActionsForNight(1);
        assertEquals(1, actions.stream()
                .filter(action -> action.getActorId().equals(simulation.sheriff().getUser().getId()))
                .count());
        assertEquals(NightActionType.CHECK, actions.get(0).getActionType());
        assertEquals("Let's see...", actions.get(0).getComment());
    }

    @Test
    void cancelledCommentIsNeverAnnounced() {
        Simulation simulation = newSimulation();

        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.sheriff().getUser().getId(), simulation.mafia().getUser().getId(),
                        NightActionType.KILL, 1, "Deleted comment"));
        actionService.cancelNightAction(simulation.game(), simulation.sheriff().getUser().getId(), 1);
        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.mafia().getUser().getId(), null, NightActionType.SKIP, 1));

        actionService.resolveNightActions(simulation.game(), 1);

        assertTrue(simulation.game().getPublicMessages().stream()
                .noneMatch(message -> message.contains("Deleted comment")));
    }

    @Test
    void mafiaKillCommentIsAnnouncedOnceWithRoleAttributionAndWithoutActorUsername() {
        Simulation simulation = newSimulation();

        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.sheriff().getUser().getId(), simulation.sheriff().getUser().getId(),
                        NightActionType.CHECK, 1));
        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.mafia().getUser().getId(), simulation.townOne().getUser().getId(),
                        NightActionType.KILL, 1, "Squee"));

        actionService.resolveNightActions(simulation.game(), 1);

        List<String> commentedMessages = simulation.game().getPublicMessages().stream()
                .filter(message -> message.contains("Mafia comments: Squee"))
                .toList();
        assertEquals(1, commentedMessages.size());
        assertFalse(commentedMessages.get(0).contains("MafiaPlayer comments"));
    }

    @Test
    void noCommentLeavesMafiaKillResultTextWithoutCommentSuffix() {
        Simulation simulation = newSimulation();

        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.sheriff().getUser().getId(), simulation.sheriff().getUser().getId(),
                        NightActionType.CHECK, 1));
        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.mafia().getUser().getId(), simulation.townOne().getUser().getId(),
                        NightActionType.KILL, 1));

        actionService.resolveNightActions(simulation.game(), 1);

        assertTrue(simulation.game().getPublicMessages().stream()
                .anyMatch(message -> message.contains("The Mafia earns a blood bonus") && !message.contains("comments:")));
    }

    @Test
    void sheriffCheckCommentDoesNotLeakPrivateRoleIntelToPublicChat() {
        Simulation simulation = newSimulation();

        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.sheriff().getUser().getId(), simulation.mafia().getUser().getId(),
                        NightActionType.CHECK, 1, "Checking quietly"));
        actionService.submitNightAction(simulation.game(),
                new NightAction(simulation.mafia().getUser().getId(), null, NightActionType.SKIP, 1));

        actionService.resolveNightActions(simulation.game(), 1);

        assertTrue(simulation.game().getPublicMessages().stream()
                .anyMatch(message -> message.contains("Sheriff comments: Checking quietly")));
        assertTrue(simulation.game().getPublicMessages().stream()
                .noneMatch(message -> message.contains("is a Mafia")));
        assertTrue(privateMessagingService.getMessagesForPlayer(simulation.sheriff().getUser().getId()).stream()
                .anyMatch(message -> message.contains("MafiaPlayer") && message.contains("Mafia")));
    }

    @Test
    void commentDoesNotAffectNightCompletionOrMafiaSharedReward() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame sheriff = player(3L, "Sheriff", sheriffRole);
        PlayerInGame target = player(4L, "TownTarget", townsfolkRole);
        GameSessionRuntime game = game(List.of(mafiaOne, mafiaTwo, sheriff, target));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(
                mafiaOne.getUser().getId(),
                mafiaTwo.getUser().getId()
        )));

        actionService.submitNightAction(game,
                new NightAction(sheriff.getUser().getId(), sheriff.getUser().getId(),
                        NightActionType.CHECK, 1));
        assertFalse(actionService.allNightActionsComplete(game, 1));

        actionService.submitNightAction(game,
                new NightAction(mafiaOne.getUser().getId(), target.getUser().getId(),
                        NightActionType.KILL, 1, "Shared bonus?"));
        assertTrue(actionService.allNightActionsComplete(game, 1));

        actionService.resolveNightActions(game, 1);

        assertEquals(11, mafiaOne.getInGameMoney());
        assertEquals(11, mafiaTwo.getInGameMoney());
    }

    @Test
    void inactiveMafiaCannotInjectCommentThroughForbiddenEndpoint() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame town = player(3L, "Town", townsfolkRole);
        GameSessionRuntime game = game(List.of(mafiaOne, mafiaTwo, town));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(
                mafiaOne.getUser().getId(),
                mafiaTwo.getUser().getId()
        )));
        when(gameManagerService.getActiveGames()).thenReturn(List.of(game));
        when(gameManagerService.getGameById(game.getSessionId())).thenReturn(game);

        ActionController controller = new ActionController(actionService, gameManagerService, mock(DigService.class));
        ResponseEntity<String> response = controller.mafiaKill(
                game.getGame().getId(),
                mafiaTwo.getUser().getId(),
                Map.of("targetUserId", town.getUser().getId(), "comment", "Sneaky")
        );

        assertEquals(403, response.getStatusCode().value());
        assertTrue(game.getActionsForNight(1).isEmpty());
    }

    private Simulation newSimulation() {
        PlayerInGame mafia = player(1L, "MafiaPlayer", mafiaRole);
        PlayerInGame sheriff = player(2L, "SheriffPlayer", sheriffRole);
        PlayerInGame townOne = player(3L, "TownOne", townsfolkRole);
        PlayerInGame townTwo = player(4L, "TownTwo", townsfolkRole);
        GameSessionRuntime game = game(List.of(mafia, sheriff, townOne, townTwo));

        return new Simulation(game, mafia, sheriff, townOne, townTwo);
    }

    private GameSessionRuntime game(List<PlayerInGame> players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(4);
        game.setPlayers(new ArrayList<>(players));
        game.getStageData().put("mafiaOrder", new ArrayList<>(players.stream()
                .filter(player -> player.getRole() != null)
                .filter(player -> player.getRole().getRoleName().equalsIgnoreCase("mafia"))
                .map(player -> player.getUser().getId())
                .toList()));
        game.getStageData().put("currentMafiaIndex", 0);
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));
        return game;
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
            PlayerInGame townOne,
            PlayerInGame townTwo
    ) {
    }
}
