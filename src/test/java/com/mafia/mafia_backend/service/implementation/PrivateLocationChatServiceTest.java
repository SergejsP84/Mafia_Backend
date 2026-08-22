package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.PrivateLocationMessageDTO;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.MembershipType;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PrivateLocationChatServiceTest {

    private PrivateLocationService privateLocationService;
    private PrivateLocationChatService chatService;
    private ActionService actionService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;

    @BeforeEach
    void setUp() {
        privateLocationService = new PrivateLocationService();
        chatService = new PrivateLocationChatService(privateLocationService);
        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "privateLocationService", privateLocationService);
        ReflectionTestUtils.setField(actionService, "privateLocationChatService", chatService);

        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
    }

    @Test
    void nativeAndInvitedOfficeMembersCanChatAndOutsiderCannot() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame invited = player(2L, "Invited", townsfolkRole);
        PlayerInGame outsider = player(3L, "Outsider", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, invited, outsider);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());

        chatService.postUserMessage(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), "  Watch Alice  ");
        chatService.postUserMessage(game, PrivateLocation.OFFICE, invited.getUser().getId(), "Understood");

        List<PrivateLocationMessageDTO> messages = chatService.getMessages(game, PrivateLocation.OFFICE, invited.getUser().getId());
        assertEquals(2, messages.size());
        assertEquals("Watch Alice", messages.get(0).text());
        assertEquals("Sheriff", messages.get(0).senderName());
        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.OFFICE, outsider.getUser().getId()));
        assertThrows(SecurityException.class,
                () -> chatService.postUserMessage(game, PrivateLocation.OFFICE, outsider.getUser().getId(), "Let me in"));
    }

    @Test
    void locationsAreIsolatedAndDualMemberCanReadBoth() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame dual = player(3L, "Dual", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, dual);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), dual.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), dual.getUser().getId());

        chatService.postUserMessage(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), "Office only");
        chatService.postUserMessage(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), "Hideout only");

        assertEquals("Office only", chatService.getMessages(game, PrivateLocation.OFFICE, dual.getUser().getId()).get(0).text());
        assertEquals("Hideout only", chatService.getMessages(game, PrivateLocation.HIDEOUT, dual.getUser().getId()).get(0).text());
        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId()));
        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.OFFICE, mafia.getUser().getId()));
    }

    @Test
    void membershipChangesImmediatelyChangeChatAccess() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame invited = player(2L, "Invited", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, invited);

        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.OFFICE, invited.getUser().getId()));

        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());
        chatService.postUserMessage(game, PrivateLocation.OFFICE, invited.getUser().getId(), "I am in.");
        assertEquals(1, chatService.getMessages(game, PrivateLocation.OFFICE, invited.getUser().getId()).size());

        privateLocationService.banish(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());
        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.OFFICE, invited.getUser().getId()));
    }

    @Test
    void deathCleanupImmediatelyRemovesOfficeAndHideoutChatAccess() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), sheriff.getUser().getId());

        chatService.postUserMessage(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), "Office");
        chatService.postUserMessage(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId(), "Hideout");

        privateLocationService.removeFromLivingLocations(game, sheriff.getUser().getId());

        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.OFFICE, sheriff.getUser().getId()));
        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId()));
    }

    @Test
    void nativeSheriffAndMafiaActionsCreateImmediateReportsInNativeLocationOnly() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame target = player(3L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, target);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());

        actionService.submitNightAction(game, new NightAction(sheriff.getUser().getId(), target.getUser().getId(), NightActionType.CHECK, 1, "secret note"));
        actionService.submitNightAction(game, new NightAction(mafia.getUser().getId(), target.getUser().getId(), NightActionType.KILL, 1));

        List<PrivateLocationMessageDTO> office = chatService.getMessages(game, PrivateLocation.OFFICE, sheriff.getUser().getId());
        List<PrivateLocationMessageDTO> hideout = chatService.getMessages(game, PrivateLocation.HIDEOUT, mafia.getUser().getId());

        assertEquals(1, office.size());
        assertEquals("ACTION_REPORT", office.get(0).type());
        assertEquals("Sheriff decided to check Target.", office.get(0).text());
        assertFalse(office.get(0).text().contains("secret note"));
        assertEquals("Mafia decided to kill Target.", hideout.get(0).text());
        assertTrue(game.getPublicMessages().stream().noneMatch(message -> message.contains("secret note")));
        assertTrue(game.getPublicMessages().stream().noneMatch(message -> message.contains("decided to check Target")));
    }

    @Test
    void invitedInfiltratorCanChatButTheirNativeActionReportsOnlyToHideout() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame target = player(3L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, target);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());

        chatService.postUserMessage(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "I am totally helpful.");
        actionService.submitNightAction(game, new NightAction(mafia.getUser().getId(), target.getUser().getId(), NightActionType.KILL, 1));

        List<PrivateLocationMessageDTO> office = chatService.getMessages(game, PrivateLocation.OFFICE, sheriff.getUser().getId());
        List<PrivateLocationMessageDTO> hideout = chatService.getMessages(game, PrivateLocation.HIDEOUT, mafia.getUser().getId());

        assertEquals(1, office.size());
        assertEquals("USER_MESSAGE", office.get(0).type());
        assertEquals("I am totally helpful.", office.get(0).text());
        assertEquals(1, hideout.size());
        assertEquals("Mafia decided to kill Target.", hideout.get(0).text());
    }

    @Test
    void rejectedActionCreatesNoReport() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame invited = player(2L, "Invited", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, invited);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());

        assertThrows(IllegalArgumentException.class, () -> actionService.submitNightAction(
                game,
                new NightAction(sheriff.getUser().getId(), invited.getUser().getId(), NightActionType.KILL, 1)));

        assertTrue(chatService.getMessages(game, PrivateLocation.OFFICE, sheriff.getUser().getId()).isEmpty());
    }

    @Test
    void replacementAndCancellationCreateHistoricalReports() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame first = player(2L, "Alice", townsfolkRole);
        PlayerInGame second = player(3L, "Bob", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, first, second);

        actionService.submitNightAction(game, new NightAction(sheriff.getUser().getId(), first.getUser().getId(), NightActionType.KILL, 1));
        actionService.submitNightAction(game, new NightAction(sheriff.getUser().getId(), second.getUser().getId(), NightActionType.KILL, 1));
        actionService.cancelNightAction(game, sheriff.getUser().getId(), 1);
        actionService.cancelNightAction(game, sheriff.getUser().getId(), 1);

        List<PrivateLocationMessageDTO> messages = chatService.getMessages(game, PrivateLocation.OFFICE, sheriff.getUser().getId());
        assertEquals(3, messages.size());
        assertEquals("Sheriff decided to kill Alice.", messages.get(0).text());
        assertEquals("Sheriff changed their target and decided to kill Bob.", messages.get(1).text());
        assertEquals("Sheriff cancelled their planned action.", messages.get(2).text());
        assertTrue(game.getActionsForNight(1).isEmpty());
    }

    @Test
    void graveyardUsesSameAccessRuleWhenMembershipExists() {
        PlayerInGame necro = player(1L, "Necro", townsfolkRole);
        PlayerInGame outsider = player(2L, "Outsider", townsfolkRole);
        GameSessionRuntime game = initializedGame(necro, outsider);
        game.getPrivateLocationState().putMembership(PrivateLocation.GRAVEYARD, necro.getUser().getId(), MembershipType.NATIVE, null);

        chatService.postUserMessage(game, PrivateLocation.GRAVEYARD, necro.getUser().getId(), "Bones report.");

        assertEquals(1, chatService.getMessages(game, PrivateLocation.GRAVEYARD, necro.getUser().getId()).size());
        assertThrows(SecurityException.class,
                () -> chatService.getMessages(game, PrivateLocation.GRAVEYARD, outsider.getUser().getId()));
    }

    @Test
    void messagesAreGameScoped() {
        PlayerInGame sheriffOne = player(1L, "SheriffOne", sheriffRole);
        PlayerInGame sheriffTwo = player(1L, "SheriffOne", sheriffRole);
        GameSessionRuntime first = initializedGame(sheriffOne);
        GameSessionRuntime second = initializedGame(sheriffTwo);

        chatService.postUserMessage(first, PrivateLocation.OFFICE, sheriffOne.getUser().getId(), "Game A");

        assertEquals(1, chatService.getMessages(first, PrivateLocation.OFFICE, sheriffOne.getUser().getId()).size());
        assertTrue(chatService.getMessages(second, PrivateLocation.OFFICE, sheriffTwo.getUser().getId()).isEmpty());
    }

    private GameSessionRuntime initializedGame(PlayerInGame... players) {
        GameSessionRuntime game = new GameSessionRuntime(mock(ConfigSettingService.class));
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(players.length);
        game.setPlayers(new ArrayList<>(List.of(players)));
        game.getStageData().put("mafiaOrder", mafiaOrder(players));
        game.getStageData().put("currentMafiaIndex", 0);
        privateLocationService.initializeNativeMemberships(game);
        return game;
    }

    private List<Long> mafiaOrder(PlayerInGame... players) {
        return List.of(players).stream()
                .filter(player -> player.getRole() != null)
                .filter(player -> player.getRole().getRoleName().equalsIgnoreCase("mafia"))
                .map(player -> player.getUser().getId())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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
