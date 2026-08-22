package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.dto.VoiceResponse;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceServiceTest {

    private VoiceService voiceService;
    private GameEconomyService economyService;
    private ActionService actionService;
    private PrivateLocationService privateLocationService;
    private PrivateLocationChatService privateLocationChatService;
    private PrivateLocationKnowledgeVaultService vaultService;

    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;
    private Role ghostRole;
    private Role vampireRole;

    @BeforeEach
    void setUp() {
        voiceService = new VoiceService();
        economyService = new GameEconomyService();
        privateLocationService = new PrivateLocationService();
        privateLocationChatService = new PrivateLocationChatService(privateLocationService);
        vaultService = new PrivateLocationKnowledgeVaultService(privateLocationService);

        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "gameEconomyService", economyService);
        ReflectionTestUtils.setField(actionService, "voiceService", voiceService);
        ReflectionTestUtils.setField(actionService, "privateLocationService", privateLocationService);

        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
        ghostRole = new Role(4L, "Ghost", Alignment.UNDEAD, false, false, false, "Spectator");
        vampireRole = new Role(5L, "Vampire", Alignment.UNDEAD, true, false, false, "Future night voice role");
    }

    @Test
    void eligibilityUsesCurrentRoleTierAliveStateAndPhase() {
        assertRejected(sheriffRole, 2, true, GamePhase.NIGHT);
        assertAccepted(sheriffRole, 3, true, GamePhase.NIGHT);
        assertAccepted(sheriffRole, 4, true, GamePhase.NIGHT);
        assertRejected(mafiaRole, 2, true, GamePhase.NIGHT);
        assertAccepted(mafiaRole, 3, true, GamePhase.NIGHT);
        assertRejected(townsfolkRole, 3, true, GamePhase.NIGHT);
        assertRejected(townsfolkRole, 4, true, GamePhase.NIGHT);
        assertRejected(sheriffRole, 3, false, GamePhase.NIGHT);
        assertRejected(sheriffRole, 3, true, GamePhase.LOBBY);
        assertRejected(sheriffRole, 3, true, GamePhase.ROLE_ASSIGNMENT);
        assertRejected(sheriffRole, 3, true, GamePhase.ENDED);
    }

    @Test
    void activeGameplayPhasesAllowOrdinaryVoice() {
        for (GamePhase phase : List.of(GamePhase.NIGHT, GamePhase.DAY_RESULTS, GamePhase.DAY_VOTING,
                GamePhase.LYNCHING, GamePhase.HANGING_DEFENSE, GamePhase.HANGING_CONFIRMATION, GamePhase.CONTRACTS)) {
            PlayerInGame sheriff = player(1L, "SecretSheriff", sheriffRole, 3, true);
            GameSessionRuntime game = game(phase, sheriff);

            voiceService.voice(game, sheriff.getUser().getId(), "Phase " + phase);

            assertEquals(1, game.getPublicMessages().size());
        }
    }

    @Test
    void currentTierChangesImmediatelyAffectEligibility() {
        PlayerInGame sheriff = player(1L, "SecretSheriff", sheriffRole, 2, true);
        sheriff.setInGameMoney(139);
        GameSessionRuntime game = game(GamePhase.NIGHT, sheriff);

        assertThrows(IllegalStateException.class,
                () -> voiceService.voice(game, sheriff.getUser().getId(), "Too early"));

        economyService.adjustMoney(game, sheriff, 1, "Test tier advance");
        voiceService.voice(game, sheriff.getUser().getId(), "Now I speak");

        economyService.adjustMoney(game, sheriff, -1, "Test tier drop");
        assertThrows(IllegalStateException.class,
                () -> voiceService.voice(game, sheriff.getUser().getId(), "Too late"));
    }

    @Test
    void publicOutputUsesCurrentRoleAndConcealsIdentity() {
        PlayerInGame player = player(7L, "FormerBumPig", mafiaRole, 3, true);
        player.setRole(sheriffRole);
        player.setAlignment(sheriffRole.getAlignment());
        GameSessionRuntime game = game(GamePhase.DAY_VOTING, player);

        VoiceResponse response = voiceService.voice(game, player.getUser().getId(), "  Stay indoors.  ");

        assertEquals("Sheriff", response.role());
        assertEquals("Stay indoors.", response.message());
        assertEquals(1, game.getPublicMessages().size());
        String message = game.getPublicMessages().get(0);
        assertTrue(message.contains("[MafiaBOT]: Sheriff: Stay indoors."));
        assertFalse(message.contains("FormerBumPig"));
        assertFalse(message.contains("Bum"));
    }

    @Test
    void multipleMafiaVoicesRemainRoleAnonymous() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole, 3, true);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole, 3, true);
        GameSessionRuntime game = game(GamePhase.NIGHT, mafiaOne, mafiaTwo);

        voiceService.voice(game, mafiaOne.getUser().getId(), "First");
        voiceService.voice(game, mafiaTwo.getUser().getId(), "Second");

        assertEquals(2, game.getPublicMessages().size());
        assertTrue(game.getPublicMessages().get(0).contains("Mafia: First"));
        assertTrue(game.getPublicMessages().get(1).contains("Mafia: Second"));
        assertFalse(game.getPublicMessages().get(0).contains("MafiaOne"));
        assertFalse(game.getPublicMessages().get(1).contains("MafiaTwo"));
    }

    @Test
    void validationNormalizesAndRejectsInvalidTextWithoutPublishing() {
        PlayerInGame sheriff = player(1L, "SecretSheriff", sheriffRole, 3, true);
        GameSessionRuntime game = game(GamePhase.NIGHT, sheriff);

        assertThrows(IllegalArgumentException.class,
                () -> voiceService.voice(game, sheriff.getUser().getId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> voiceService.voice(game, sheriff.getUser().getId(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> voiceService.voice(game, sheriff.getUser().getId(), "   "));
        assertThrows(IllegalArgumentException.class,
                () -> voiceService.voice(game, sheriff.getUser().getId(), "x".repeat(257)));
        assertTrue(game.getPublicMessages().isEmpty());

        voiceService.voice(game, sheriff.getUser().getId(), "x".repeat(256));

        assertEquals(1, game.getPublicMessages().size());
        assertTrue(game.getPublicMessages().get(0).contains("x".repeat(256)));
    }

    @Test
    void voiceDoesNotAffectNightActionsMafiaRotationEconomyOrTier() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole, 3, true);
        mafia.setInGameMoney(140);
        PlayerInGame sheriff = player(2L, "Sheriff", sheriffRole, 3, true);
        sheriff.setInGameMoney(140);
        PlayerInGame target = player(3L, "Target", townsfolkRole, 1, true);
        GameSessionRuntime game = game(GamePhase.NIGHT, mafia, sheriff, target);
        game.getStageData().put("mafiaOrder", new ArrayList<>(List.of(mafia.getUser().getId())));
        game.getStageData().put("currentMafiaIndex", 0);

        actionService.submitNightAction(game, new NightAction(mafia.getUser().getId(), target.getUser().getId(), NightActionType.KILL, 1));
        voiceService.voice(game, mafia.getUser().getId(), "Good night.");

        assertEquals(1, game.getActionsForNight(1).size());
        assertEquals(NightActionType.KILL, game.getActionsForNight(1).get(0).getActionType());
        assertTrue(mafia.isHasActedTonight());
        assertFalse(sheriff.isHasActedTonight());
        assertFalse(actionService.allNightActionsComplete(game, 1));
        assertEquals(0, game.getStageData().get("currentMafiaIndex"));
        assertEquals(140, mafia.getInGameMoney());
        assertEquals(3, mafia.getTier());

        PlayerInGame sheriffOnly = player(4L, "SheriffOnly", sheriffRole, 3, true);
        sheriffOnly.setInGameMoney(140);
        GameSessionRuntime voiceFirst = game(GamePhase.NIGHT, sheriffOnly);
        voiceService.voice(voiceFirst, sheriffOnly.getUser().getId(), "I have not acted.");
        assertFalse(sheriffOnly.isHasActedTonight());
        assertTrue(voiceFirst.getActionsForNight(1).isEmpty());
    }

    @Test
    void voiceDoesNotTouchPrivateLocationReportsOrVaults() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole, 3, true);
        PlayerInGame mafia = player(2L, "Mafia", mafiaRole, 3, true);
        GameSessionRuntime game = game(GamePhase.NIGHT, sheriff, mafia);
        privateLocationService.initializeNativeMemberships(game);

        voiceService.voice(game, sheriff.getUser().getId(), "Public only.");

        assertEquals(1, game.getPublicMessages().size());
        assertTrue(game.getMessagesForPrivateLocation(PrivateLocation.OFFICE).isEmpty());
        assertTrue(game.getMessagesForPrivateLocation(PrivateLocation.HIDEOUT).isEmpty());
        assertTrue(vaultService.getKnowledgeForMember(game, PrivateLocation.OFFICE, sheriff.getUser().getId()).isEmpty());
    }

    @Test
    void catalogueShowsVoiceOnlyWhenCurrentlyEligible() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole, 3, true);
        PlayerInGame lowTierSheriff = player(2L, "LowSheriff", sheriffRole, 2, true);
        PlayerInGame town = player(3L, "Town", townsfolkRole, 4, true);
        PlayerInGame deadSheriff = player(4L, "DeadSheriff", sheriffRole, 3, false);

        assertTrue(hasVoice(actionService.computeActionsFor(game(GamePhase.NIGHT, sheriff), sheriff)));
        assertFalse(hasVoice(actionService.computeActionsFor(game(GamePhase.NIGHT, lowTierSheriff), lowTierSheriff)));
        assertFalse(hasVoice(actionService.computeActionsFor(game(GamePhase.NIGHT, town), town)));
        assertFalse(hasVoice(actionService.computeActionsFor(game(GamePhase.NIGHT, deadSheriff), deadSheriff)));
    }

    @Test
    void futureUndeadVoiceRulesAreCentralized() {
        PlayerInGame ghost = player(1L, "Ghost", ghostRole, 4, false);
        PlayerInGame vampire = player(2L, "Vampire", vampireRole, 3, false);

        assertFalse(voiceService.canVoice(game(GamePhase.NIGHT, ghost), ghost));
        assertTrue(voiceService.canVoice(game(GamePhase.NIGHT, vampire), vampire));
        assertFalse(voiceService.canVoice(game(GamePhase.DAY_VOTING, vampire), vampire));
    }

    private void assertAccepted(Role role, int tier, boolean alive, GamePhase phase) {
        PlayerInGame player = player(1L, "Player", role, tier, alive);
        assertTrue(voiceService.canVoice(game(phase, player), player));
    }

    private void assertRejected(Role role, int tier, boolean alive, GamePhase phase) {
        PlayerInGame player = player(1L, "Player", role, tier, alive);
        assertFalse(voiceService.canVoice(game(phase, player), player));
    }

    private boolean hasVoice(NightActionCatalogDTO catalog) {
        return catalog.actions().stream().anyMatch(action -> action.code().equals("VOICE"));
    }

    private GameSessionRuntime game(GamePhase phase, PlayerInGame... players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(phase);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(players.length);
        game.setPlayers(new ArrayList<>(List.of(players)));
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));
        return game;
    }

    private PlayerInGame player(Long id, String username, Role role, int tier, boolean alive) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setMoney(600L);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        player.setTier(tier);
        player.setAlive(alive);
        player.setInGameMoney(switch (tier) {
            case 2 -> 60;
            case 3 -> 140;
            case 4 -> 240;
            default -> 0;
        });
        return player;
    }
}
