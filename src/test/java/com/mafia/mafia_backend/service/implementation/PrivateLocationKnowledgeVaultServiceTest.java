package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.PrivateLocationKnowledgeDTO;
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
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.game.GameRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateLocationKnowledgeVaultServiceTest {

    private PrivateLocationService privateLocationService;
    private PrivateLocationKnowledgeVaultService vaultService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;

    @BeforeEach
    void setUp() {
        privateLocationService = new PrivateLocationService();
        vaultService = new PrivateLocationKnowledgeVaultService(privateLocationService);

        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
    }

    @Test
    void genericStorageIsLocationScopedAndOverwrites() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);
        game.getPrivateLocationState().putMembership(PrivateLocation.GRAVEYARD, sheriff.getUser().getId(), MembershipType.NATIVE, null);

        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Hacker");
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Mafia");
        vaultService.recordKnowledge(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId(), "Sheriff");
        vaultService.recordKnowledge(game, PrivateLocation.GRAVEYARD, mafia.getUser().getId(), "Vampire");

        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId()).size());
        assertEquals("Mafia", vaultService.getKnownRole(game, PrivateLocation.OFFICE, mafia.getUser().getId()).orElseThrow().perceivedRole());
        assertEquals("Sheriff", vaultService.getKnownRole(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId()).orElseThrow().perceivedRole());
        assertEquals("Vampire", vaultService.getKnowledgeForMember(game, PrivateLocation.GRAVEYARD, sheriff.getUser().getId()).get(0).role());
    }

    @Test
    void vaultStoresSuppliedPerceivedRoleWithoutInspectingActualRole() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);

        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Hacker");

        assertEquals("Mafia", mafia.getRole().getRoleName());
        assertEquals("Hacker", vaultService.getKnownRole(game, PrivateLocation.OFFICE, mafia.getUser().getId()).orElseThrow().perceivedRole());
    }

    @Test
    void accessAllowsNativeAndInvitedButRejectsOutsidersAndCrossLocationReaders() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame invited = player(3L, "Invited", townsfolkRole);
        PlayerInGame outsider = player(4L, "Outsider", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, invited, outsider);
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());
        privateLocationService.invite(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), invited.getUser().getId());
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Mafia");
        vaultService.recordKnowledge(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId(), "Sheriff");

        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId()).size());
        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, invited.getUser().getId()).size());
        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.HIDEOUT, invited.getUser().getId()).size());
        assertThrows(SecurityException.class,
                () -> vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, outsider.getUser().getId()));
        assertThrows(SecurityException.class,
                () -> vaultService.getKnowledgeForMember(game, PrivateLocation.HIDEOUT, sheriff.getUser().getId()));
    }

    @Test
    void membershipLifecycleChangesAccessButNotRecords() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame invited = player(3L, "Invited", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, invited);
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Mafia");

        assertThrows(SecurityException.class,
                () -> vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, invited.getUser().getId()));
        privateLocationService.invite(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());
        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, invited.getUser().getId()).size());
        privateLocationService.banish(game, PrivateLocation.OFFICE, sheriff.getUser().getId(), invited.getUser().getId());
        assertThrows(SecurityException.class,
                () -> vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, invited.getUser().getId()));
        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId()).size());
    }

    @Test
    void recordedPlayerDeathRemovesSubjectFromAllVaultsButKeepsUnrelatedRecords() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        PlayerInGame town = player(3L, "Town", townsfolkRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia, town);
        game.getPrivateLocationState().putMembership(PrivateLocation.GRAVEYARD, sheriff.getUser().getId(), MembershipType.NATIVE, null);
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Mafia");
        vaultService.recordKnowledge(game, PrivateLocation.HIDEOUT, mafia.getUser().getId(), "Mafia");
        vaultService.recordKnowledge(game, PrivateLocation.GRAVEYARD, mafia.getUser().getId(), "Mafia");
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, town.getUser().getId(), "Townsfolk");

        GameManagerService gameManagerService = gameManagerServiceWithVault();
        gameManagerService.handlePlayerDeath(game, mafia, sheriff.getUser().getId());

        assertTrue(vaultService.getKnownRole(game, PrivateLocation.OFFICE, mafia.getUser().getId()).isEmpty());
        assertTrue(vaultService.getKnownRole(game, PrivateLocation.HIDEOUT, mafia.getUser().getId()).isEmpty());
        assertTrue(vaultService.getKnownRole(game, PrivateLocation.GRAVEYARD, mafia.getUser().getId()).isEmpty());
        assertEquals("Townsfolk", vaultService.getKnownRole(game, PrivateLocation.OFFICE, town.getUser().getId()).orElseThrow().perceivedRole());
    }

    @Test
    void knowledgeProducerDeathDoesNotEraseSubjectKnowledge() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Mafia");

        GameManagerService gameManagerService = gameManagerServiceWithVault();
        gameManagerService.handlePlayerDeath(game, sheriff, mafia.getUser().getId());

        assertEquals("Mafia", vaultService.getKnownRole(game, PrivateLocation.OFFICE, mafia.getUser().getId()).orElseThrow().perceivedRole());
        assertThrows(SecurityException.class,
                () -> vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId()));
    }

    @Test
    void sheriffCheckWritesResolvedRoleToOfficeVaultOnlyDuringResolution() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);

        ActionService actionService = new ActionService();
        PrivateMessagingService privateMessagingService = new PrivateMessagingService();
        ConfigSettingService configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        ReflectionTestUtils.setField(actionService, "privateMessagingService", privateMessagingService);
        ReflectionTestUtils.setField(actionService, "gameEconomyService", new GameEconomyService());
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "privateLocationService", privateLocationService);
        ReflectionTestUtils.setField(actionService, "privateLocationKnowledgeVaultService", vaultService);
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);

        actionService.submitNightAction(game, new NightAction(sheriff.getUser().getId(), mafia.getUser().getId(), NightActionType.CHECK, 1));
        assertTrue(vaultService.getKnownRole(game, PrivateLocation.OFFICE, mafia.getUser().getId()).isEmpty());

        actionService.resolveNightActions(game, 1);

        assertEquals("Mafia", vaultService.getKnownRole(game, PrivateLocation.OFFICE, mafia.getUser().getId()).orElseThrow().perceivedRole());
        assertTrue(privateMessagingService.getMessagesForPlayer(sheriff.getUser().getId()).stream()
                .anyMatch(message -> message.contains("Mafia")));
    }

    @Test
    void returnedDtoListCannotMutateAuthoritativeVault() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole);
        GameSessionRuntime game = initializedGame(sheriff, mafia);
        vaultService.recordKnowledge(game, PrivateLocation.OFFICE, mafia.getUser().getId(), "Mafia");

        List<PrivateLocationKnowledgeDTO> view = vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId());

        assertThrows(UnsupportedOperationException.class, () -> view.clear());
        assertEquals(1, vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId()).size());
    }

    @Test
    void knowledgeIsGameScoped() {
        PlayerInGame sheriffOne = player(1L, "SheriffOne", sheriffRole);
        PlayerInGame mafiaOne = player(2L, "MafiaOne", mafiaRole);
        PlayerInGame sheriffTwo = player(1L, "SheriffOne", sheriffRole);
        PlayerInGame mafiaTwo = player(2L, "MafiaOne", mafiaRole);
        GameSessionRuntime first = initializedGame(sheriffOne, mafiaOne);
        GameSessionRuntime second = initializedGame(sheriffTwo, mafiaTwo);

        vaultService.recordKnowledge(first, PrivateLocation.OFFICE, mafiaOne.getUser().getId(), "Mafia");

        assertEquals(1, vaultService.getKnowledgeForMember(first, PrivateLocation.OFFICE, sheriffOne.getUser().getId()).size());
        assertTrue(vaultService.getKnowledgeForMember(second, PrivateLocation.OFFICE, sheriffTwo.getUser().getId()).isEmpty());
    }

    private GameManagerService gameManagerServiceWithVault() {
        GameManagerService gameManagerService = new GameManagerService(
                mock(ConfigSettingRepository.class),
                mock(RoleRepository.class),
                mock(UserService.class),
                mock(ConfigSettingService.class),
                mock(RoleRefusalTrackerRepository.class),
                mock(GameRegistry.class));
        ReflectionTestUtils.setField(gameManagerService, "privateMessagingService", new PrivateMessagingService());
        ReflectionTestUtils.setField(gameManagerService, "privateLocationService", privateLocationService);
        ReflectionTestUtils.setField(gameManagerService, "privateLocationKnowledgeVaultService", vaultService);
        return gameManagerService;
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
