package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.RoleRefusalTracker;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameManagerRoleDistributionTest {

    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;
    private RoleRepository roleRepository;
    private UserService userService;
    private GameManagerService gameManagerService;

    @BeforeEach
    void setUp() {
        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");

        roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByRoleName(anyString())).thenAnswer(invocation -> {
            String roleName = invocation.getArgument(0);
            if ("Mafia".equalsIgnoreCase(roleName)) return Optional.of(mafiaRole);
            if ("Sheriff".equalsIgnoreCase(roleName)) return Optional.of(sheriffRole);
            if ("Townsfolk".equalsIgnoreCase(roleName)) return Optional.of(townsfolkRole);
            return Optional.empty();
        });

        userService = mock(UserService.class);
        when(userService.getOrCreateTracker(anyLong())).thenAnswer(invocation -> {
            RoleRefusalTracker tracker = new RoleRefusalTracker();
            tracker.setUserId(invocation.getArgument(0));
            return tracker;
        });
        gameManagerService = new GameManagerService(
                mock(ConfigSettingRepository.class),
                roleRepository,
                userService,
                mock(ConfigSettingService.class),
                mock(RoleRefusalTrackerRepository.class),
                mock(GameRegistry.class)
        );
        ReflectionTestUtils.setField(gameManagerService, "privateMessagingService", new PrivateMessagingService());
    }

    @Test
    void mafiaCountFollowsBoundaryStaircase() {
        Map<Integer, Integer> expectedCounts = Map.ofEntries(
                Map.entry(4, 1),
                Map.entry(8, 1),
                Map.entry(9, 2),
                Map.entry(14, 2),
                Map.entry(15, 3),
                Map.entry(20, 3),
                Map.entry(21, 4),
                Map.entry(26, 4),
                Map.entry(27, 5),
                Map.entry(33, 6),
                Map.entry(39, 7)
        );

        expectedCounts.forEach((playerCount, mafiaCount) ->
                assertEquals(mafiaCount, gameManagerService.calculateMafiaCount(playerCount),
                        playerCount + " players"));
    }

    @Test
    void generatedTemporaryRostersUseOnlyMafiaSheriffAndTownsfolk() {
        for (int playerCount : List.of(4, 8, 9, 15, 21)) {
            List<String> roles = gameManagerService.buildRoleNamesForGame(playerCount);
            int expectedMafia = gameManagerService.calculateMafiaCount(playerCount);

            assertEquals(playerCount, roles.size(), playerCount + " players");
            assertEquals(expectedMafia, count(roles, "Mafia"), playerCount + " players");
            assertEquals(1, count(roles, "Sheriff"), playerCount + " players");
            assertEquals(playerCount - expectedMafia - 1, count(roles, "Townsfolk"), playerCount + " players");
            assertTrue(roles.stream().allMatch(role ->
                    role.equals("Mafia") || role.equals("Sheriff") || role.equals("Townsfolk")));
        }
    }

    @Test
    void roleSummaryReflectsGeneratedRoster() {
        assertEquals(
                "There are 9 prominent people living in Maf City: 2 Mafia, 1 Sheriff, 6 Townsfolk.",
                gameManagerService.generateRoleSummary(9)
        );
    }

    @Test
    void assignmentCreatesDistinctPendingMafiaOffersAndMafiaOrder() {
        GameSessionRuntime game = newGame(9);

        gameManagerService.assignInitialRoles(game);

        assertEquals(9, game.getInitialPlayerCount());
        assertEquals(2, pendingKeys(game, "pending_Mafia_").count());
        assertEquals(1, pendingKeys(game, "pending_Sheriff_").count());

        @SuppressWarnings("unchecked")
        List<Long> mafiaOrder = (List<Long>) game.getStageData().get("mafiaOrder");
        assertEquals(2, mafiaOrder.size());
        assertEquals(2, game.getPlayers().stream()
                .filter(p -> p.getRole() != null)
                .filter(p -> p.getRole().getRoleName().equals("Mafia"))
                .count());
    }

    @Test
    void confirmingMultipleMafiaOffersAssignsFullTemporaryRoster() {
        GameSessionRuntime game = newGame(15);
        gameManagerService.getActiveGames().add(game);
        when(userService.getUserById(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return game.findPlayerById(userId).orElseThrow().getUser();
        });

        gameManagerService.assignInitialRoles(game);
        List<Long> pendingUserIds = game.getStageData().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("pending_"))
                .filter(entry -> !entry.getKey().endsWith("_timestamp"))
                .map(entry -> (Long) entry.getValue())
                .toList();

        pendingUserIds.forEach(userId -> gameManagerService.confirmOfferedRole(game.getGame().getId(), userId));

        List<String> assignedRoles = game.getPlayers().stream()
                .map(player -> player.getRole().getRoleName())
                .toList();

        assertEquals(15, assignedRoles.size());
        assertEquals(3, count(assignedRoles, "Mafia"));
        assertEquals(1, count(assignedRoles, "Sheriff"));
        assertEquals(11, count(assignedRoles, "Townsfolk"));
        assertEquals(GamePhase.NIGHT, game.getStage());

        @SuppressWarnings("unchecked")
        List<Long> mafiaOrder = (List<Long>) game.getStageData().get("mafiaOrder");
        assertEquals(3, mafiaOrder.size());
    }

    @Test
    void fourPlayerRosterRemainsUnchanged() {
        List<String> roles = gameManagerService.buildRoleNamesForGame(4);

        assertEquals(4, roles.size());
        assertEquals(1, count(roles, "Mafia"));
        assertEquals(1, count(roles, "Sheriff"));
        assertEquals(2, count(roles, "Townsfolk"));
    }

    @Test
    void tierThresholdAssignmentUsesCapturedInitialPlayerCount() {
        GameSessionRuntime game = newGame(16);
        gameManagerService.assignInitialRoles(game);
        game.getPlayers().subList(4, 16).clear();

        gameManagerService.assignTierThresholds(game);

        assertEquals(16, game.getInitialPlayerCount());
        @SuppressWarnings("unchecked")
        Map<String, Integer> thresholds = (Map<String, Integer>) game.getStageData().get("tierThresholds");
        assertEquals(60, thresholds.get("tier2"));
        assertEquals(140, thresholds.get("tier3"));
        assertEquals(240, thresholds.get("tier4"));
    }

    private GameSessionRuntime newGame(int playerCount) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.ROLE_ASSIGNMENT);
        game.setPlayers(new ArrayList<>());

        for (long id = 1; id <= playerCount; id++) {
            User user = new User();
            user.setId(id);
            user.setUsername("TestPiglet" + id);

            PlayerInGame player = new PlayerInGame();
            player.setUser(user);
            player.setAlive(true);
            game.getPlayers().add(player);
        }

        return game;
    }

    private long count(List<String> roles, String roleName) {
        return roles.stream().filter(roleName::equals).count();
    }

    private Stream<String> pendingKeys(GameSessionRuntime game, String prefix) {
        return game.getStageData().keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .filter(key -> !key.endsWith("_timestamp"));
    }
}
