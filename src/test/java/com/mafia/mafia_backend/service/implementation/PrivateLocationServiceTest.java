package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.LocationMembersDTO;
import com.mafia.mafia_backend.domain.dto.LocationMembershipResponse;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.MembershipType;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.game.GameRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PrivateLocationServiceTest {

    private PrivateLocationService privateLocationService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;

    @BeforeEach
    void setUp() {
        privateLocationService = new PrivateLocationService();
        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
    }

    @Test
    void initialNativeMembershipsUseConfirmedRolesOnly() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        PlayerInGame townsfolk = player(3L, "TownPig", townsfolkRole);
        GameSessionRuntime game = game(sheriff, mafia, townsfolk);

        privateLocationService.initializeNativeMemberships(game);

        assertTrue(privateLocationService.isNativeMember(game, sheriff.getUser().getId(), PrivateLocation.OFFICE));
        assertTrue(privateLocationService.isNativeMember(game, mafia.getUser().getId(), PrivateLocation.HIDEOUT));
        assertFalse(privateLocationService.isMember(game, townsfolk.getUser().getId(), PrivateLocation.OFFICE));
        assertFalse(privateLocationService.isMember(game, townsfolk.getUser().getId(), PrivateLocation.HIDEOUT));
        assertEquals(List.of(sheriff.getUser().getId()), game.getStageData().get("office_members"));
        assertEquals(List.of(mafia.getUser().getId()), game.getStageData().get("hideout_members"));
        assertEquals(List.of(), game.getStageData().get("graveyard_members"));
    }

    @Test
    void officeAndHideoutAllowCrossFactionInvitesWithoutDuplicates() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        PlayerInGame townsfolk = player(3L, "TownPig", townsfolkRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia, townsfolk);

        LocationMembershipResponse response = privateLocationService.invite(
                game,
                PrivateLocation.OFFICE,
                sheriff.getUser().getId(),
                mafia.getUser().getId());
        LocationMembershipResponse duplicate = privateLocationService.invite(
                game,
                PrivateLocation.OFFICE,
                sheriff.getUser().getId(),
                mafia.getUser().getId());

        assertTrue(response.changed());
        assertFalse(duplicate.changed());
        assertEquals(MembershipType.INVITED, privateLocationService
                .getMembershipType(game, mafia.getUser().getId(), PrivateLocation.OFFICE)
                .orElseThrow());
        assertEquals(2, privateLocationService.getMembers(game, PrivateLocation.OFFICE).size());
        assertFalse(privateLocationService.isMember(game, townsfolk.getUser().getId(), PrivateLocation.OFFICE));
    }

    @Test
    void invitedMemberCanInviteAndPlayerCanBelongToMultipleLocations() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        PlayerInGame townsfolk = player(3L, "TownPig", townsfolkRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia, townsfolk);

        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.OFFICE, mafia.getUser().getId(), townsfolk.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), sheriff.getUser().getId());

        assertTrue(privateLocationService.isMember(game, mafia.getUser().getId(), PrivateLocation.HIDEOUT));
        assertTrue(privateLocationService.isMember(game, mafia.getUser().getId(), PrivateLocation.OFFICE));
        assertTrue(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.OFFICE));
        assertTrue(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.HIDEOUT));
        assertTrue(privateLocationService.isMember(game, townsfolk.getUser().getId(), PrivateLocation.OFFICE));
    }

    @Test
    void banishRemovesOnlyInvitedMembersFromThatLocation() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia);

        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());
        privateLocationService.banish(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), mafia.getUser().getId());

        assertFalse(privateLocationService.isMember(game, mafia.getUser().getId(), PrivateLocation.OFFICE));
        assertTrue(privateLocationService.isMember(game, mafia.getUser().getId(), PrivateLocation.HIDEOUT));
        assertThrows(IllegalStateException.class, () -> privateLocationService.banish(
                game,
                PrivateLocation.HIDEOUT,
                mafia.getUser().getId(),
                mafia.getUser().getId()));
    }

    @Test
    void graveyardManualChangesAreRejected() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia);

        assertThrows(IllegalStateException.class, () -> privateLocationService.invite(
                game,
                PrivateLocation.GRAVEYARD,
                sheriff.getUser().getId(),
                mafia.getUser().getId()));
        assertThrows(IllegalStateException.class, () -> privateLocationService.banish(
                game,
                PrivateLocation.GRAVEYARD,
                sheriff.getUser().getId(),
                mafia.getUser().getId()));
    }

    @Test
    void memberListsArePrivateToMembers() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        PlayerInGame townsfolk = player(3L, "TownPig", townsfolkRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia, townsfolk);

        LocationMembersDTO visible = privateLocationService.getVisibleMembers(
                game,
                PrivateLocation.OFFICE,
                sheriff.getUser().getId());

        assertEquals(1, visible.members().size());
        assertEquals("SheriffPig", visible.members().get(0).username());
        assertThrows(SecurityException.class, () -> privateLocationService.getVisibleMembers(
                game,
                PrivateLocation.OFFICE,
                townsfolk.getUser().getId()));
    }

    @Test
    void deathCleanupRemovesOfficeAndHideoutButNotGraveyard() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia);

        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), sheriff.getUser().getId());
        game.getPrivateLocationState().putMembership(
                PrivateLocation.GRAVEYARD,
                sheriff.getUser().getId(),
                MembershipType.NATIVE,
                null);

        privateLocationService.removeFromLivingLocations(game, sheriff.getUser().getId());

        assertFalse(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.OFFICE));
        assertFalse(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.HIDEOUT));
        assertTrue(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.GRAVEYARD));
    }

    @Test
    void gameManagerDeathHookRemovesLivingLocationMemberships() {
        PlayerInGame sheriff = player(1L, "SheriffPig", sheriffRole);
        PlayerInGame mafia = player(2L, "MafiaPig", mafiaRole);
        GameSessionRuntime game = initializedGame(10L, sheriff, mafia);

        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), sheriff.getUser().getId());

        GameManagerService gameManagerService = new GameManagerService(
                mock(ConfigSettingRepository.class),
                mock(RoleRepository.class),
                mock(UserService.class),
                mock(ConfigSettingService.class),
                mock(RoleRefusalTrackerRepository.class),
                mock(GameRegistry.class));
        ReflectionTestUtils.setField(gameManagerService, "privateMessagingService", new PrivateMessagingService());
        ReflectionTestUtils.setField(gameManagerService, "privateLocationService", privateLocationService);

        gameManagerService.handlePlayerDeath(game, sheriff, mafia.getUser().getId());

        assertFalse(sheriff.isAlive());
        assertFalse(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.OFFICE));
        assertFalse(privateLocationService.isMember(game, sheriff.getUser().getId(), PrivateLocation.HIDEOUT));
        assertTrue(privateLocationService.isMember(game, mafia.getUser().getId(), PrivateLocation.HIDEOUT));
    }

    @Test
    void membershipsAreGameScoped() {
        PlayerInGame sheriffOne = player(1L, "SheriffOne", sheriffRole);
        PlayerInGame mafiaOne = player(2L, "MafiaOne", mafiaRole);
        PlayerInGame sheriffTwo = player(1L, "SheriffOne", sheriffRole);
        PlayerInGame townTwo = player(3L, "TownTwo", townsfolkRole);
        GameSessionRuntime firstGame = initializedGame(10L, sheriffOne, mafiaOne);
        GameSessionRuntime secondGame = initializedGame(20L, sheriffTwo, townTwo);

        privateLocationService.invite(firstGame, PrivateLocation.OFFICE, sheriffOne.getUser().getId(), mafiaOne.getUser().getId());

        assertTrue(privateLocationService.isMember(firstGame, mafiaOne.getUser().getId(), PrivateLocation.OFFICE));
        assertFalse(privateLocationService.isMember(secondGame, townTwo.getUser().getId(), PrivateLocation.OFFICE));
        assertEquals(1, privateLocationService.getMembers(secondGame, PrivateLocation.OFFICE).size());
    }

    private GameSessionRuntime initializedGame(Long gameId, PlayerInGame... players) {
        GameSessionRuntime game = game(players);
        privateLocationService.initializeNativeMemberships(game);
        return game;
    }

    private GameSessionRuntime game(PlayerInGame... players) {
        GameSessionRuntime game = new GameSessionRuntime(mock(ConfigSettingService.class));
        Game entity = new Game();
        game.setGame(entity);
        game.getPlayers().addAll(List.of(players));
        return game;
    }

    private PlayerInGame player(Long userId, String username, Role role) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        PlayerInGame player = new PlayerInGame(user, true);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        return player;
    }
}
