package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.DigResponse;
import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DigServiceTest {

    private UserRepository userRepository;
    private GameEconomyService economyService;
    private DigService digService;
    private ActionService actionService;

    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;
    private Role ghostRole;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        economyService = new GameEconomyService();
        digService = new DigService(userRepository, economyService);

        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "gameEconomyService", economyService);

        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
        ghostRole = new Role(4L, "Ghost", Alignment.UNDEAD, false, false, false, "Dead observer");
    }

    @Test
    void successfulDigDebitsPermanentMoneyCreditsInGameMoneyAndMarksUsed() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 600);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        DigResponse response = digService.dig(game, player.getUser().getId(), 20);

        assertEquals(600, response.permanentAccountDebited());
        assertEquals(0, response.newPermanentBalance());
        assertEquals(20, response.newInGameBalance());
        assertEquals(20, player.getInGameMoney());
        assertEquals(0, player.getUser().getMoney());
        assertTrue(player.isHasDugThisGame());
    }

    @Test
    void affordabilityLimitRejectsTooMuchAndAllowsMaximumAffordableAmount() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 870);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        assertThrows(IllegalArgumentException.class,
                () -> digService.dig(game, player.getUser().getId(), 30));
        assertEquals(870, player.getUser().getMoney());
        assertEquals(0, player.getInGameMoney());
        assertFalse(player.isHasDugThisGame());

        DigResponse response = digService.dig(game, player.getUser().getId(), 29);

        assertEquals(870, response.permanentAccountDebited());
        assertEquals(0, response.newPermanentBalance());
        assertEquals(29, response.newInGameBalance());
    }

    @Test
    void digCapUsesSeventyFivePercentOfTierTwoThreshold() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 2000);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        assertEquals(45, economyService.getMaxDigAmount(game));
        assertThrows(IllegalArgumentException.class,
                () -> digService.dig(game, player.getUser().getId(), 46));

        DigResponse response = digService.dig(game, player.getUser().getId(), 45);

        assertEquals(45, response.newInGameBalance());
        assertEquals(650, response.newPermanentBalance());
    }

    @Test
    void diggingFromZeroCannotReachTierTwoByItself() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 2000);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        digService.dig(game, player.getUser().getId(), 45);

        assertEquals(45, player.getInGameMoney());
        assertEquals(1, player.getTier());
    }

    @Test
    void diggingCanImmediatelyAdvanceTierThroughEconomyService() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 600);
        player.setInGameMoney(120);
        player.setTier(2);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        DigResponse response = digService.dig(game, player.getUser().getId(), 20);

        assertEquals(140, player.getInGameMoney());
        assertEquals(3, player.getTier());
        assertEquals(3, response.tier());
    }

    @Test
    void diggingCanJumpMultipleTiersWhenThresholdsAllowIt() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 1500);
        player.setInGameMoney(50);
        player.setTier(1);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 70,
                "tier4", 80
        ));

        digService.dig(game, player.getUser().getId(), 45);

        assertEquals(95, player.getInGameMoney());
        assertEquals(4, player.getTier());
    }

    @Test
    void oncePerGameRuleOnlyConsumesSuccessfulDigAndSurvivesRoleChange() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 900);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        assertThrows(IllegalArgumentException.class,
                () -> digService.dig(game, player.getUser().getId(), 46));
        assertFalse(player.isHasDugThisGame());

        digService.dig(game, player.getUser().getId(), 10);
        player.setRole(mafiaRole);
        player.setAlignment(mafiaRole.getAlignment());

        assertThrows(IllegalStateException.class,
                () -> digService.dig(game, player.getUser().getId(), 10));
        assertEquals(10, player.getInGameMoney());
    }

    @Test
    void mafiaDiggingIsPersonalAndDoesNotConsumeNightAction() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole, 600);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole, 600);
        PlayerInGame sheriff = player(3L, "Sheriff", sheriffRole, 600);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(mafiaOne, mafiaTwo, sheriff));
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(mafiaOne.getUser().getId(), mafiaTwo.getUser().getId())));
        game.getStageData().put("currentMafiaIndex", 0);

        digService.dig(game, mafiaOne.getUser().getId(), 20);

        assertEquals(20, mafiaOne.getInGameMoney());
        assertEquals(0, mafiaTwo.getInGameMoney());
        assertFalse(mafiaOne.isHasActedTonight());
        assertFalse(actionService.allNightActionsComplete(game, 1));
    }

    @Test
    void diggingIsNightOnlyAndRejectsLockedNight() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 600);

        for (GamePhase phase : List.of(GamePhase.LOBBY, GamePhase.ROLE_ASSIGNMENT, GamePhase.DAY_RESULTS,
                GamePhase.DAY_VOTING, GamePhase.ENDED)) {
            GameSessionRuntime game = game(phase, 16, List.of(player));
            assertThrows(IllegalStateException.class,
                    () -> digService.dig(game, player.getUser().getId(), 1));
        }

        GameSessionRuntime locked = game(GamePhase.NIGHT, 16, List.of(player));
        locked.getStageData().put("nightResolved", true);

        assertThrows(IllegalStateException.class,
                () -> digService.dig(locked, player.getUser().getId(), 1));
    }

    @Test
    void ghostCannotDig() {
        PlayerInGame player = player(1L, "GhostPiglet", ghostRole, 600);
        player.setAlive(false);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        assertThrows(IllegalStateException.class,
                () -> digService.dig(game, player.getUser().getId(), 1));
        assertFalse(player.isHasDugThisGame());
    }

    @Test
    void publicMessageIncludesRoleAndAmountButNotUsername() {
        PlayerInGame player = player(1L, "SecretPiglet", sheriffRole, 600);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        digService.dig(game, player.getUser().getId(), 20);

        List<String> digMessages = game.getPublicMessages().stream()
                .filter(message -> message.contains("dig up $20"))
                .toList();
        assertEquals(1, digMessages.size());
        assertTrue(digMessages.get(0).contains("Sheriff"));
        assertFalse(digMessages.get(0).contains("SecretPiglet"));
    }

    @Test
    void rejectedDigProducesNoPublicMessageAndNoMoneyMovement() {
        PlayerInGame player = player(1L, "TestPiglet", townsfolkRole, 600);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        assertThrows(IllegalArgumentException.class,
                () -> digService.dig(game, player.getUser().getId(), 0));

        assertTrue(game.getPublicMessages().isEmpty());
        assertEquals(600, player.getUser().getMoney());
        assertEquals(0, player.getInGameMoney());
        assertFalse(player.isHasDugThisGame());
    }

    @Test
    void actionCatalogueShowsDigBeforeUseAndReflectsTierAfterDig() {
        PlayerInGame player = player(1L, "Sheriff", sheriffRole, 600);
        player.setInGameMoney(50);
        player.setTier(1);
        GameSessionRuntime game = game(GamePhase.NIGHT, 16, List.of(player));

        NightActionCatalogDTO before = actionService.computeActionsFor(game, player);
        assertTrue(before.actions().stream().anyMatch(action -> action.code().equals("DIG")));

        digService.dig(game, player.getUser().getId(), 10);

        assertEquals(2, player.getTier());
        NightActionCatalogDTO after = actionService.computeActionsFor(game, player);
        assertTrue(after.actions().stream().noneMatch(action -> action.code().equals("DIG")));
    }

    private GameSessionRuntime game(GamePhase phase, int initialPlayerCount, List<PlayerInGame> players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(phase);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(initialPlayerCount);
        game.setPlayers(new ArrayList<>(players));
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));
        return game;
    }

    private PlayerInGame player(Long id, String username, Role role, long permanentMoney) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setMoney(permanentMoney);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        player.setAlive(true);
        return player;
    }
}
