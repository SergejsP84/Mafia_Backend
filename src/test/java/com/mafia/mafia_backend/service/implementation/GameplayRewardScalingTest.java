package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.model.ResultRecord;
import com.mafia.mafia_backend.domain.model.VerdictChoice;
import com.mafia.mafia_backend.domain.model.VoteRecord;
import com.mafia.mafia_backend.process.GamePhaseScheduler;
import com.mafia.mafia_backend.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameplayRewardScalingTest {

    private GamePhaseScheduler scheduler;
    private ActionService actionService;

    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;
    private Role neutralRole;

    @BeforeEach
    void setUp() {
        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
        neutralRole = new Role(4L, "Bum", Alignment.NEUTRAL, false, false, false, "Neutral drifter");

        ConfigSettingService configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        GameManagerService gameManagerService = mock(GameManagerService.class);
        doAnswer(invocation -> {
            PlayerInGame victim = invocation.getArgument(1);
            victim.setAlive(false);
            return null;
        }).when(gameManagerService).handlePlayerDeath(any(), any(), anyLong());

        GameEconomyService economyService = new GameEconomyService();
        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "gameManagerService", gameManagerService);
        ReflectionTestUtils.setField(actionService, "privateMessagingService", new PrivateMessagingService());
        ReflectionTestUtils.setField(actionService, "gameEconomyService", economyService);

        scheduler = new GamePhaseScheduler(
                gameManagerService,
                mock(RoleRepository.class),
                mock(UserService.class),
                configSettingService,
                new VictoryService(),
                actionService,
                new PrivateMessagingService(),
                economyService
        );
        ReflectionTestUtils.setField(scheduler, "gameHistoryService", mock(GameHistoryService.class));
    }

    @Test
    void lynchRewardsScalePositiveAndNegativeAmounts() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame sheriff = player(2L, "Sheriff", sheriffRole);
        PlayerInGame target = player(3L, "TownTarget", townsfolkRole);
        PlayerInGame town = player(4L, "Town", townsfolkRole);
        PlayerInGame nonVotingMafia = player(5L, "NonVotingMafia", mafiaRole);
        GameSessionRuntime game = game(4, GamePhase.LYNCHING, List.of(mafia, sheriff, target, town, nonVotingMafia));
        game.getStageData().put("lynchStartedAt", LocalDateTime.now().minusSeconds(30));
        game.getStageData().put("lynchTarget", target.getUser().getUsername());
        game.getStageData().put("currentTally", Map.of(target.getUser().getUsername(), 2L));
        game.getDayVotes().put(mafia.getUser().getId(),
                new VoteRecord(mafia.getUser().getId(), target.getUser().getId(), false, LocalDateTime.now()));
        game.getDayVotes().put(sheriff.getUser().getId(),
                new VoteRecord(sheriff.getUser().getId(), target.getUser().getId(), false, LocalDateTime.now()));

        ReflectionTestUtils.invokeMethod(scheduler, "handleLynchingPhase", game);

        assertEquals(18, mafia.getInGameMoney());
        assertEquals(2, nonVotingMafia.getInGameMoney());
        assertEquals(-14, sheriff.getInGameMoney());
    }

    @Test
    void mafiaLynchPenaltyAppliesOnlyToMafiaWhoVotedForMafiaHanging() {
        PlayerInGame votingMafia = player(1L, "VotingMafia", mafiaRole);
        PlayerInGame targetMafia = player(2L, "TargetMafia", mafiaRole);
        PlayerInGame nonVotingMafia = player(3L, "NonVotingMafia", mafiaRole);
        PlayerInGame town = player(4L, "Town", townsfolkRole);
        GameSessionRuntime game = game(4, GamePhase.LYNCHING, List.of(votingMafia, targetMafia, nonVotingMafia, town));
        game.getStageData().put("lynchStartedAt", LocalDateTime.now().minusSeconds(30));
        game.getStageData().put("lynchTarget", targetMafia.getUser().getUsername());
        game.getStageData().put("currentTally", Map.of(targetMafia.getUser().getUsername(), 1L));
        game.getDayVotes().put(votingMafia.getUser().getId(),
                new VoteRecord(votingMafia.getUser().getId(), targetMafia.getUser().getId(), false, LocalDateTime.now()));

        ReflectionTestUtils.invokeMethod(scheduler, "handleLynchingPhase", game);

        assertEquals(-17, votingMafia.getInGameMoney());
        assertEquals(2, nonVotingMafia.getInGameMoney());
        assertEquals(0, targetMafia.getInGameMoney());
    }

    @Test
    void guiltyVerdictRewardAppliesOnlyToMafiaWhoVotedGuilty() {
        PlayerInGame votingMafia = player(1L, "VotingMafia", mafiaRole);
        PlayerInGame nonVotingMafia = player(2L, "NonVotingMafia", mafiaRole);
        PlayerInGame accusedTown = player(3L, "AccusedTown", townsfolkRole);
        GameSessionRuntime game = game(4, GamePhase.HANGING_CONFIRMATION,
                List.of(votingMafia, nonVotingMafia, accusedTown));
        game.setAccusedUserId(accusedTown.getUser().getId());
        game.getStageData().put("phaseStartedAt", LocalDateTime.now().minusSeconds(60));
        game.castVerdictVote(votingMafia.getUser().getId(), VerdictChoice.GUILTY);

        ReflectionTestUtils.invokeMethod(scheduler, "handleHangingConfirmationPhase", game);

        assertEquals(18, votingMafia.getInGameMoney());
        assertEquals(2, nonVotingMafia.getInGameMoney());
        assertEquals(0, accusedTown.getInGameMoney());
    }

    @Test
    void daySurvivalBonusUsesSurvivalScaler() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame neutral = player(2L, "Neutral", neutralRole);
        PlayerInGame deadMafia = player(3L, "DeadMafia", mafiaRole);
        deadMafia.setAlive(false);
        GameSessionRuntime game = game(4, GamePhase.CONTRACTS, List.of(mafia, neutral, deadMafia));

        scheduler.applyAndAnnounceDaySurvivalBonuses(game);

        assertEquals(2, mafia.getInGameMoney());
        assertEquals(1, neutral.getInGameMoney());
        assertEquals(0, deadMafia.getInGameMoney());
    }

    @Test
    void hangingSurvivalBonusAppliesOnlyToLivingQualifyingVillains() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame neutral = player(2L, "Neutral", neutralRole);
        PlayerInGame hangedMafia = player(3L, "HangedMafia", mafiaRole);
        hangedMafia.setAlive(false);
        GameSessionRuntime game = game(4, GamePhase.CONTRACTS, List.of(mafia, neutral, hangedMafia));

        scheduler.applyAndAnnounceHangingBonuses(game);

        assertEquals(2, mafia.getInGameMoney());
        assertEquals(1, neutral.getInGameMoney());
        assertEquals(0, hangedMafia.getInGameMoney());
    }

    @Test
    void daySurvivalBonusUsesCapsInLargeGames() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame neutral = player(2L, "Neutral", neutralRole);
        GameSessionRuntime game = game(60, GamePhase.CONTRACTS, List.of(mafia, neutral));

        scheduler.applyAndAnnounceDaySurvivalBonuses(game);

        assertEquals(12, mafia.getInGameMoney());
        assertEquals(10, neutral.getInGameMoney());
    }

    @Test
    void victoryBonusScalesInLargerGame() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame town = player(2L, "Town", townsfolkRole);
        town.setAlive(false);
        GameSessionRuntime game = game(25, GamePhase.ENDED, List.of(mafia, town));
        game.getStageData().put("winnerAlignment", Alignment.MAFIA);
        game.getStageData().put("winnerAnnouncement", "Mafia wins");

        ReflectionTestUtils.invokeMethod(scheduler, "handleGameEndedPhase", game);

        assertEquals(150, mafia.getInGameMoney());
        assertEquals(150, mafia.getUser().getMoney());
    }

    @Test
    void drawConsolationScales() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole);
        PlayerInGame town = player(2L, "Town", townsfolkRole);
        GameSessionRuntime game = game(4, GamePhase.ENDED, List.of(mafia, town));
        game.getStageData().put("winnerAlignment", Alignment.NONE);
        game.getStageData().put("winnerAnnouncement", "Draw");
        game.getStageData().put("isDraw", true);

        ReflectionTestUtils.invokeMethod(scheduler, "handleGameEndedPhase", game);

        assertEquals(13, mafia.getInGameMoney());
        assertEquals(13, town.getInGameMoney());
    }

    @Test
    void repeatedSkipPenaltyRemainsExact() {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole);
        sheriff.setSkipCount(2);
        GameSessionRuntime game = game(4, GamePhase.NIGHT, List.of(sheriff));

        ResultRecord record = new ResultRecord();
        record.setActorId(sheriff.getUser().getId());

        ReflectionTestUtils.invokeMethod(actionService, "applySkipEffect", game, record);

        assertEquals(-50, sheriff.getInGameMoney());
        assertEquals(1, sheriff.getTier());
        assertEquals(false, sheriff.isAlive());
    }

    private GameSessionRuntime game(int initialPlayerCount, GamePhase phase, List<PlayerInGame> players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(phase);
        game.setInitialPlayerCount(initialPlayerCount);
        game.setPlayers(new ArrayList<>(players));
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
}
