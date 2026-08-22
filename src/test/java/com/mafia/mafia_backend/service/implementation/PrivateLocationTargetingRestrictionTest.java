package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.controller.ActionController;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.ActionDisposition;
import com.mafia.mafia_backend.domain.enums.ActionType;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.repository.UserRepository;
import com.mafia.mafia_backend.service.game.GameRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateLocationTargetingRestrictionTest {

    private ActionService actionService;
    private PrivateLocationService privateLocationService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;

    @BeforeEach
    void setUp() {
        actionService = new ActionService();
        privateLocationService = new PrivateLocationService();
        ReflectionTestUtils.setField(actionService, "gameEconomyService", new GameEconomyService());
        ReflectionTestUtils.setField(actionService, "privateLocationService", privateLocationService);

        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
    }

    @Test
    void sheriffNativeOfficeCannotKillInvitedOfficeMemberButCanCheckThem() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame invitedTown = player(2L, "InvitedTown", townsfolkRole);
        PlayerInGame outsider = player(3L, "Outsider", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, invitedTown, outsider);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invitedTown.getUser().getId());

        assertBlocked(game, sheriff, invitedTown, NightActionType.KILL);
        assertAllowed(game, sheriff, invitedTown, NightActionType.CHECK);
        assertAllowed(game, sheriff, outsider, NightActionType.KILL);
    }

    @Test
    void activeMafiaNativeHideoutCannotKillNativeOrInvitedHideoutMemberButCanKillOutsider() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame invitedTown = player(3L, "InvitedTown", townsfolkRole);
        PlayerInGame outsider = player(4L, "Outsider", townsfolkRole);
        GameSessionRuntime game = initializedGame(mafiaOne, mafiaTwo, invitedTown, outsider);
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafiaOne.getUser().getId(), invitedTown.getUser().getId());

        assertBlocked(game, mafiaOne, mafiaTwo, NightActionType.KILL);
        assertBlocked(game, mafiaOne, invitedTown, NightActionType.KILL);
        assertAllowed(game, mafiaOne, outsider, NightActionType.KILL);
    }

    @Test
    void invitedMemberCanUseDetrimentalActionAgainstNativeMember() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());

        assertAllowed(game, mafia, sheriff, NightActionType.KILL);
    }

    @Test
    void banishmentImmediatelyRemovesVoluntaryTargetingRestriction() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());

        assertBlocked(game, sheriff, mafia, NightActionType.KILL);
        privateLocationService.banish(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());
        assertAllowed(game, sheriff, mafia, NightActionType.KILL);
    }

    @Test
    void anyNativeSharedMembershipBlocksEvenWhenAnotherSharedMembershipIsInvited() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame target = player(3L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, target);

        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), target.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), sheriff.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), target.getUser().getId());

        assertBlocked(game, sheriff, target, NightActionType.KILL);
    }

    @Test
    void noNativeSharedMembershipMeansDetrimentalActionIsAllowed() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame target = player(3L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, target);

        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), sheriff.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), target.getUser().getId());

        assertAllowed(game, sheriff, target, NightActionType.KILL);
    }

    @Test
    void dispositionAndTargetTypeControlTheRestriction() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame target = player(2L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, target);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), target.getUser().getId());

        assertThrows(IllegalArgumentException.class, () -> actionService.validateVoluntaryTargeting(
                game, sheriff.getUser().getId(), target.getUser().getId(), ActionType.TARGET_PLAYER, ActionDisposition.DETRIMENTAL));
        assertDoesNotThrow(() -> actionService.validateVoluntaryTargeting(
                game, sheriff.getUser().getId(), target.getUser().getId(), ActionType.TARGET_PLAYER, ActionDisposition.NEUTRAL));
        assertDoesNotThrow(() -> actionService.validateVoluntaryTargeting(
                game, sheriff.getUser().getId(), target.getUser().getId(), ActionType.TARGET_PLAYER, ActionDisposition.BENEFICIAL));
        assertDoesNotThrow(() -> actionService.validateVoluntaryTargeting(
                game, sheriff.getUser().getId(), target.getUser().getId(), ActionType.TARGET_PLAYER, ActionDisposition.NONE));
        assertDoesNotThrow(() -> actionService.validateVoluntaryTargeting(
                game, sheriff.getUser().getId(), target.getUser().getId(), ActionType.GLOBAL, ActionDisposition.DETRIMENTAL));
    }

    @Test
    void skipDigAndShopAreUnaffectedByPrivateLocationTargetingRestriction() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame target = player(2L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, target);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), target.getUser().getId());

        assertDoesNotThrow(() -> actionService.submitNightAction(
                game,
                new NightAction(sheriff.getUser().getId(), null, NightActionType.SKIP, 1)));

        UserRepository userRepository = mock(UserRepository.class);
        DigService digService = new DigService(userRepository, new GameEconomyService());
        target.getUser().setMoney(60L);
        when(userRepository.findById(target.getUser().getId())).thenReturn(Optional.of(target.getUser()));
        when(userRepository.saveAndFlush(target.getUser())).thenReturn(target.getUser());
        assertDoesNotThrow(() -> digService.dig(game, target.getUser().getId(), 1));

        ShopService shopService = new ShopService(new GameEconomyService());
        shopService.initializeShop(game);
        target.setInGameMoney(100);
        assertDoesNotThrow(() -> shopService.buy(game, target.getUser().getId(), com.mafia.mafia_backend.domain.enums.ShopProductCode.CROSS));
    }

    @Test
    void illegalActionWithCommentDoesNotStoreActionOrComment() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame target = player(2L, "Target", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, target);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), target.getUser().getId());

        assertThrows(IllegalArgumentException.class, () -> actionService.submitNightAction(
                game,
                new NightAction(sheriff.getUser().getId(), target.getUser().getId(), NightActionType.KILL, 1, "Forbidden")));

        assertTrue(game.getActionsForNight(1).isEmpty());
        assertFalse(sheriff.isHasActedTonight());
    }

    @Test
    void actionControllerRejectsProhibitedMafiaTargetAndPreservesInactiveMafiaReason() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole);
        PlayerInGame outsider = player(3L, "Outsider", townsfolkRole);
        GameSessionRuntime game = initializedGame(mafiaOne, mafiaTwo, outsider);

        GameManagerService gameManagerService = new GameManagerService(
                mock(ConfigSettingRepository.class),
                mock(RoleRepository.class),
                mock(UserService.class),
                mock(ConfigSettingService.class),
                mock(RoleRefusalTrackerRepository.class),
                mock(GameRegistry.class));
        gameManagerService.getActiveGames().add(game);

        ActionController controller = new ActionController(actionService, gameManagerService, mock(DigService.class), mock(VoiceService.class));

        ResponseEntity<String> inactiveResponse = controller.mafiaKill(
                game.getGame().getId(),
                mafiaTwo.getUser().getId(),
                Map.of("targetUserId", outsider.getUser().getId()));
        ResponseEntity<String> friendlyFireResponse = controller.mafiaKill(
                game.getGame().getId(),
                mafiaOne.getUser().getId(),
                Map.of("targetUserId", mafiaTwo.getUser().getId(), "comment", "No witnesses"));

        assertEquals(HttpStatus.FORBIDDEN, inactiveResponse.getStatusCode());
        assertEquals("Only the active Mafia may submit a Mafia action tonight.", inactiveResponse.getBody());
        assertEquals(HttpStatus.BAD_REQUEST, friendlyFireResponse.getStatusCode());
        assertTrue(friendlyFireResponse.getBody().contains("Cannot use a detrimental action"));
        assertTrue(game.getActionsForNight(1).isEmpty());
    }

    private void assertBlocked(GameSessionRuntime game, PlayerInGame actor, PlayerInGame target, NightActionType actionType) {
        assertThrows(IllegalArgumentException.class, () -> actionService.submitNightAction(
                game,
                new NightAction(actor.getUser().getId(), target.getUser().getId(), actionType, 1)));
    }

    private void assertAllowed(GameSessionRuntime game, PlayerInGame actor, PlayerInGame target, NightActionType actionType) {
        actionService.submitNightAction(
                game,
                new NightAction(actor.getUser().getId(), target.getUser().getId(), actionType, 1));
        game.cancelNightAction(1, actor.getUser().getId());
        actor.setHasActedTonight(false);
    }

    private GameSessionRuntime initializedGame(PlayerInGame... players) {
        GameSessionRuntime game = new GameSessionRuntime(mock(ConfigSettingService.class));
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setPlayers(new ArrayList<>(List.of(players)));
        game.setInitialPlayerCount(players.length);
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
