package com.mafia.mafia_backend.process;

import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.RoleRefusalTracker;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.ContractOrder;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.VerdictChoice;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.implementation.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GamePhaseScheduler {

    @Autowired
    private final GameManagerService gameManagerService;
    @Autowired
    private final RoleRepository roleRepository;
    @Autowired
    private final UserService userService;
    @Autowired
    private final ConfigSettingService configSettingService;
    @Autowired
    private final VictoryService victoryService;
    @Autowired
    private final ActionService actionService;
    @Autowired
    private GameHistoryService gameHistoryService;
    @Autowired
    private final PrivateMessagingService privateMessagingService;

    @PostConstruct
    public void init() {
        System.out.println("🐖 Mafia Scheduler initialized.");
    }

    @Scheduled(fixedRate = 5000)
    public void checkLobbyTimeouts() {
        List<GameSessionRuntime> snapshot = new ArrayList<>(gameManagerService.getActiveGames());
        for (GameSessionRuntime game : snapshot) {
            if (game == null || game.getGame() == null) continue;
            if (game.getStage() != GamePhase.LOBBY) continue;

            LocalDateTime now = LocalDateTime.now();
            long secondsElapsed = Duration.between(game.getCreatedAt(), now).getSeconds();

            int maxPlayers = gameManagerService.getMaxPlayersFromSettings(); // Config
            int lobbySeconds = gameManagerService.getLobbyDurationFromSettings(); // Config

            boolean timeExceeded = secondsElapsed >= lobbySeconds;
            boolean enoughPlayersForMax = game.getPlayers().size() >= maxPlayers;

            if (timeExceeded || enoughPlayersForMax) {
                int playerCount = game.getPlayers().size();

                if (playerCount < 4) {
                    game.setAborted(true);
                    game.advanceStage(GamePhase.CANCELED);
                    game.addPublicMessage("Not enough players. Game has been canceled.");
                    System.out.println("Game canceled due to insufficient players (" + playerCount + ").");
                    game.getPlayers().clear();
//                    gameManagerService.removeGame(game.getSessionId());
                    game.getStageData().put("cleanupScheduledAt", LocalDateTime.now());
                } else {
                    game.advanceStage(GamePhase.ROLE_ASSIGNMENT);
                    game.addLog("Game transitioned to ROLE_ASSIGNMENT phase.");
                    System.out.println("Transitioning to the role assignment phase...");
                    game.addPublicMessage(gameManagerService.pickRandomIntroMessage());
                    game.addPublicMessage(generateRoleSummary(game.getPlayers()));
                    gameManagerService.assignInitialRoles(game);
                    game.getStageData().put("roleAssignStartedAt", LocalDateTime.now());
                    boolean ghostCanAppear = false;
                    if (playerCount > configSettingService.getIntSetting("GhostThreshold", 11))
                        ghostCanAppear = true;
                    game.getStageData().put("ghostCanAppear", ghostCanAppear);
                }
            }
        }
    }

    private String generateRoleSummary(List<PlayerInGame> players) {
        int total = players.size();

        // Placeholder summary for now — we’ll update this after roles are assigned.
        if (total == 4) {
            return "There are 4 prominent people living in Maf City: 2 Townsfolk, 1 Mafia, 1 Sheriff.";
        } else {
            return "A group of " + total + " have gathered... roles will be revealed soon.";
        }
    }

    @Scheduled(fixedRate = 5000)
    public void checkRoleAssignmentTimeouts() {
        List<GameSessionRuntime> snapshot = new ArrayList<>(gameManagerService.getActiveGames());
        for (GameSessionRuntime game : snapshot) {
            if (game == null || game.getGame() == null) continue;
            if (game.getStage() == GamePhase.CANCELED || game.getStage() == GamePhase.ENDED) continue;
            if (game.getStage() != GamePhase.ROLE_ASSIGNMENT) continue;

            gameManagerService.banishIdlePlayers(game);

            long pendingCount = game.getStageData().keySet().stream()
                    .filter(k -> k.startsWith("pending_") && !k.endsWith("_timestamp"))
                    .count();

            boolean noPendingRoles = (pendingCount == 0);

            if (noPendingRoles) {
                gameManagerService.assignTownsfolkToRemainingPlayers(game);
                game.addPublicMessage("All roles accepted! The game begins!");
                System.out.println("🐽 All roles accepted! Advancing " + game.getGame().getId() + " to NIGHT phase.");
                System.out.println("🐽 Transition to NIGHT initiated from the checkRoleAssignmentTimeouts scheduler of GamePhaseScheduler");
                gameManagerService.assignPrivateChatAccess(game);
                gameManagerService.assignTierThresholds(game);
                game.advanceStage(GamePhase.NIGHT);
                game.addLog("Game moved to NIGHT phase after all roles confirmed.");
            } else {
                System.out.println("🐽🐽🐽 checkRoleAssignmentTimeouts scheduler of GamePhaseScheduler still detects pending roles in boolean noPendingRoles = game.getStageData().keySet().stream().noneMatch(k -> k.startsWith(\"pending_\"));");
            }
        }
    }

    @Scheduled(fixedRate = 3000)
    public void advanceGamePhases() {
        // 🧠 Take snapshot to avoid ConcurrentModificationException
        List<GameSessionRuntime> snapshot = new ArrayList<>(gameManagerService.getActiveGames());

        for (GameSessionRuntime game : snapshot) {
            if (game == null || game.getGame() == null) continue;
            if (game.getStage() == GamePhase.CANCELED || game.getGame().getPhase() == GamePhase.CANCELED) continue;

            GamePhase currentPhase = game.getStage();

            // 🕒 Schedule cleanup once for CANCELED or ENDED games
            if ((currentPhase == GamePhase.CANCELED || currentPhase == GamePhase.ENDED)
                    && !game.getStageData().containsKey("cleanupScheduledAt")) {
                game.getStageData().put("cleanupScheduledAt", LocalDateTime.now());
                System.out.println("🕒 Cleanup scheduled for " + currentPhase + " game " + game.getGame().getId());
                continue; // 🛑 <-- OOOOOINK!
            }

            // 🧹 Perform delayed cleanup after 10 seconds
            LocalDateTime cleanupAt = (LocalDateTime) game.getStageData().get("cleanupScheduledAt");
            if (cleanupAt != null) {
                long elapsed = Duration.between(cleanupAt, LocalDateTime.now()).getSeconds();
                if (elapsed > 10) {
                    System.out.println("🧹 Removing " + currentPhase + " game "
                            + game.getGame().getId() + " after " + elapsed + "s delay.");
                    gameManagerService.removeGame(game.getSessionId());
                    continue; // 🛑 Skip further processing for this removed game
                }
            }

            // 🐷 Process only active phases
            switch (currentPhase) {
                case NIGHT -> handleNightPhase(game);
                case DAY_RESULTS -> handleDayResultsPhase(game);
                case LYNCHING -> handleLynchingPhase(game);
                case HANGING_DEFENSE -> handleHangingDefensePhase(game);
                case HANGING_CONFIRMATION -> handleHangingConfirmationPhase(game);
                case CONTRACTS -> handleContractsPhase(game);
                case ENDED, CANCELED, LOBBY, ROLE_ASSIGNMENT -> {
                    // nothing to advance
                }
            }
        }
    }

    @Scheduled(fixedRate = 333)
    public void refreshVoteTallies() {
        List<GameSessionRuntime> snapshot = new ArrayList<>(gameManagerService.getActiveGames());
        for (GameSessionRuntime game : snapshot) {
            if (game == null || game.getGame() == null) continue;
            if (game.getStage() == GamePhase.DAY_VOTING) {
                handleDayVotingPhase(game);
            }
        }
    }

    @Scheduled(fixedRate = 333)
    public void refreshLynchTallies() {
        List<GameSessionRuntime> snapshot = new ArrayList<>(gameManagerService.getActiveGames());
        for (GameSessionRuntime game : snapshot) {
            if (game == null || game.getGame() == null) continue;
            if (game.getStage() == GamePhase.LYNCHING) {
                Map<String, Long> lynchTally = game.computeLynchTally();
                game.getStageData().put("currentTally", lynchTally);
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupVoteCaches() {
        List<GameSessionRuntime> snapshot = new ArrayList<>(gameManagerService.getActiveGames());
        for (GameSessionRuntime game : snapshot) {
            if (game == null || game.getGame() == null) continue;
            game.cleanupOldVoteTimestamps();
        }
    }

    private void notifyPlayersOfNight(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();

        // --- 🕵️ Identify Mafia rotation info ---
        List<Long> mafiaOrder = (List<Long>) stageData.get("mafiaOrder");
        Integer currentIndex = (Integer) stageData.get("currentMafiaIndex");

        // Initialize Mafia rotation if missing
        if ((mafiaOrder == null || mafiaOrder.isEmpty()) && currentIndex == null) {
            mafiaOrder = game.getPlayers().stream()
                    .filter(p -> p.isAlive() && p.getRole() != null
                            && p.getRole().getRoleName().equalsIgnoreCase("mafia"))
                    .map(p -> p.getUser().getId())
                    .collect(Collectors.toList());

            if (!mafiaOrder.isEmpty()) {
                stageData.put("mafiaOrder", mafiaOrder);
                stageData.put("currentMafiaIndex", 0);
                game.addLog("Mafia order auto-initialized at night start: " + mafiaOrder);
            }
        }

        // Fix any index mismatch
        if (mafiaOrder != null && !mafiaOrder.isEmpty()) {
            if (currentIndex == null || currentIndex >= mafiaOrder.size()) {
                currentIndex = 0;
            }
            stageData.put("currentMafiaIndex", currentIndex);
        }

        // --- 📬 Notify each role ---
        for (PlayerInGame player : game.getPlayers()) {
            if (!player.isAlive() || player.getRole() == null) continue;

            Role role = player.getRole();
            Long playerId = player.getUser().getId();
            String roleName = role.getRoleName().toLowerCase();

            switch (roleName) {
                case "mafia" -> {
                    // Multiple Mafia — use rotation
                    if (mafiaOrder != null && !mafiaOrder.isEmpty()) {
                        Long activeMafiaId = mafiaOrder.get(currentIndex);
                        if (playerId.equals(activeMafiaId)) {
                            List<String> targets = getAlivePlayerNames(game);
                            String targetList = String.join(", ", targets);
                            privateMessagingService.sendPrivateMessage(playerId,
                                    "💀 Night " + game.getCurrentNightNumber() + " begins.\n" +
                                            "The Mafia expects you to do a job tonight — don't forget your gun.\n" +
                                            "Available targets: " + targetList);
                        } else {
                            privateMessagingService.sendPrivateMessage(playerId,
                                    "💼 Night " + game.getCurrentNightNumber() + " begins.\n" +
                                            "The Mafia has no jobs for you tonight.\n" +
                                            "Bars and casinos await — enjoy the calm.");
                        }
                    } else {
                        // Single-Mafia fallback
                        List<String> targets = getAlivePlayerNames(game);
                        String targetList = String.join(", ", targets);
                        privateMessagingService.sendPrivateMessage(playerId,
                                "💀 Night " + game.getCurrentNightNumber() + " begins.\n" +
                                        "The Mafia expects you to do a job tonight.\n" +
                                        "Available targets: " + targetList);
                    }
                }

                case "sheriff" -> {
                    List<String> targets = getAlivePlayerNames(game);
                    String targetList = String.join(", ", targets);
                    privateMessagingService.sendPrivateMessage(playerId,
                            "🔫 Night " + game.getCurrentNightNumber() + " begins.\n" +
                                    "The streets are full of villains... Time to act, Sheriff.\n" +
                                    "Available targets: " + targetList);
                }

                default -> {
                    privateMessagingService.sendPrivateMessage(playerId,
                            "🌙 Night " + game.getCurrentNightNumber() + " begins.\n" +
                                    "You are " + role.getRoleName() + ". Sleep peacefully...");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void advanceMafiaRotationIfNeeded(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();
        List<Long> mafiaOrder = (List<Long>) stageData.get("mafiaOrder");
        Integer currentIndex = (Integer) stageData.get("currentMafiaIndex");

        if (mafiaOrder != null && !mafiaOrder.isEmpty()) {
            int nextIndex = (currentIndex == null) ? 0 : (currentIndex + 1) % mafiaOrder.size();
            stageData.put("currentMafiaIndex", nextIndex);
            game.addLog("Mafia rotation advanced to index " + nextIndex);
        }
    }



    private void handleNightPhase(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();

        // 🕐 Initialization — runs once per night
        LocalDateTime startedAt = (LocalDateTime) stageData.get("phaseStartedAt");
        if (startedAt == null) {
            stageData.put("phaseStartedAt", LocalDateTime.now());
            game.incrementNightNumber();
            game.addLog("Night " + game.getCurrentNightNumber() + " begins.");
            notifyPlayersOfNight(game);  // 📨 Send all role-based private messages once
            return; // Initialize only once
        }

        // 🧮 Calculate how long the night has been going
        int nightDurationSeconds = configSettingService.getIntSetting("NightPhaseDurationSeconds", 180);
        long secondsElapsed = Duration.between(startedAt, LocalDateTime.now()).getSeconds();

        boolean timeExpired = secondsElapsed >= nightDurationSeconds;
        boolean allActionsDone = gameManagerService.allNightActionsComplete(game);

        // 🩶 If neither condition is met, keep the night going — scheduler will call again
        if (!timeExpired && !allActionsDone) return;

        // 🌅 If we reached here, it means the night is over
        game.addPublicMessage("🌅 The sun rises. Night is over.");
        game.advanceStage(GamePhase.DAY_RESULTS);
        stageData.remove("phaseStartedAt"); // reset timestamp for next phase
        game.addLog("Game transitioned to DAY_RESULTS after night.");

        // 🌀 Advance Mafia rotation index
        advanceMafiaRotationIfNeeded(game);
    }

    private void handleDayVotingPhase(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();
        LocalDateTime startedAt = (LocalDateTime) stageData.get("phaseStartedAt");

        if (startedAt == null) {
            stageData.put("phaseStartedAt", LocalDateTime.now());
            game.addPublicMessage("☀️ The sun is high, citizens of Mafsville! Who is responsible for all those nightly atrocities? Time to discuss and cast your votes for hanging!");
            return;
        }

        long secondsElapsed = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
        int totalPlayers = (int) game.getPlayers().stream().filter(PlayerInGame::isAlive).count();
        int votesCast = game.getAllVotes().size();

        // Dynamic timing and thresholds
        double lynchWindow = 2 + (totalPlayers - 3) * 0.8; // seconds before lynch can expire
        int lynchThreshold = (int) Math.floor(totalPlayers / 2.0) + 1; // half + 1

        // Compute vote tally
        Map<String, Long> tally = game.computeVoteTally();

        // Check for lynch possibility
        if (votesCast >= lynchThreshold && secondsElapsed < lynchWindow && tally.size() <= 2) {
            List<String> candidateNames = tally.keySet().stream().toList();
            String candidateName1 = candidateNames.get(0);
            String candidateName2 = candidateNames.get(1);
            boolean thirdLynchCondition = (tally.get(candidateName1) > 1 && tally.get(candidateName2) <= 1) ||
                    (tally.get(candidateName2) > 1 && tally.get(candidateName1) <= 1);
            if (thirdLynchCondition) {
                String targetName = tally.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                game.addPublicMessage("⚖️ The angry townsfolk have formed a Lynch mob against " + targetName + "!");
                stageData.put("lynchTarget", targetName);
                stageData.put("lynchStartedAt", LocalDateTime.now());
                stageData.put("currentTally", tally);
                game.advanceStage(GamePhase.LYNCHING);
                return;
            }
        }
        // If no lynch formed and time is up → end voting phase
        int votingDuration = configSettingService.getIntSetting("DayVotingDurationSeconds", 120);
        if (secondsElapsed >= votingDuration || votesCast == totalPlayers) {
            Optional<PlayerInGame> topCandidateOpt = game.getTopVotedCandidate(totalPlayers);
            if (topCandidateOpt.isEmpty()) {
                // No clear majority or tie → skip hanging
                game.addPublicMessage("☀️ The townsfolk failed to reach a verdict today. No one will be hanged.");
                game.advanceStage(GamePhase.CONTRACTS);
                game.addLog("Day voting ended with no hanging target.");
                return;
            }

            PlayerInGame accused = topCandidateOpt.get();
            game.setAccusedUserId(accused.getUser().getId());
            game.addPublicMessage("⚖️ The court calls " + accused.getUser().getUsername() +
                    " to stand trial for their alleged crimes!");
            game.advanceStage(GamePhase.HANGING_DEFENSE);
            stageData.put("phaseStartedAt", LocalDateTime.now());
            game.addLog("Transitioned to HANGING_DEFENSE phase with accused " + accused.getUser().getUsername());
        }
        // Otherwise, continue waiting
    }
    private void handleDayResultsPhase(GameSessionRuntime game) {
        if (!game.beginPhaseAdvance()) return;  // skip if already running

        try {
            Map<String, Object> stageData = game.getStageData();
            if (!stageData.containsKey("nightResolved")) {
                stageData.put("nightResolved", true);
                int nightNumber = game.getCurrentNightNumber();
                game.addPublicMessage("🌅 Dawn breaks over Mafsville... the town gathers to see what happened overnight.");
                actionService.resolveNightActions(game, nightNumber);
            }
        } finally {
            game.getPlayers().forEach(p -> p.setHasActedTonight(false));
            game.endPhaseAdvance();
        }
    }

    private void handleLynchingPhase(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();
        LocalDateTime lynchStartedAt = (LocalDateTime) stageData.get("lynchStartedAt");

        if (lynchStartedAt == null) {
            stageData.put("lynchStartedAt", LocalDateTime.now());
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Long> tally = (Map<String, Long>) stageData.get("currentTally");
        long secondsElapsed = Duration.between(lynchStartedAt, LocalDateTime.now()).getSeconds();
        int totalPlayers = (int) game.getPlayers().stream().filter(PlayerInGame::isAlive).count();
        double lynchWindow = 2 + (totalPlayers - 3) * 0.8; // reuse scaling rule

        String targetName = (String) stageData.get("lynchTarget");
        if (targetName == null) {
            game.addLog("⚠️ Lynch phase reached without a target — skipping.");
            game.advanceStage(GamePhase.CONTRACTS); // safe fallback
            return;
        }

        // Allow mob join for 75% of window, then execute
        if (secondsElapsed < lynchWindow * 0.75) {
            return; // still gathering the mob
        }

        // --- Time to execute the lynch ---
        Optional<PlayerInGame> targetOpt = game.getPlayers().stream()
                .filter(p -> p.getUser().getUsername().equals(targetName))
                .findFirst();

        if (targetOpt.isEmpty()) {
            game.addLog("⚠️ Could not find lynch target " + targetName);
            game.advanceStage(GamePhase.CONTRACTS);
            return;
        }

        PlayerInGame target = targetOpt.get();
        gameManagerService.handlePlayerDeath(game, target, 0L);;
        game.addPublicMessage("💀 The bloodthirsty mob has executed " + targetName + "!");
        game.addLog("Player " + targetName + " was lynched by the mob.");
        Map<String, Integer> rewards = new HashMap<>();
        int townsfolkReward = 0;
        int mafiaReward = 0;
        int sheriffReward = 0;
        String hangingAnnouncement = "";
        switch (target.getRole().getRoleName().toLowerCase()) {
            // Will add new role cases here as more roles arrive
            case "mafia" -> {
                hangingAnnouncement = "For countless crimes against the town of Mafsville, the court has sentenced the Mafia member " + target.getUser().getUsername() + " to death by hanging, which was executed without undue delay, making the streets of Mafsville just a little bit safer this evening. Citizens, rejoice!";
                townsfolkReward = 45;
                mafiaReward = -38;
                sheriffReward = 34;
            }
            case "townsfolk" -> {
                hangingAnnouncement = "Having completely lost their nerve due to all the chaos in town, the fair citizens of Mafsville have executed their fellow innocent Townsfolk " + target.getUser().getUsername() + "! Well, justice is blind...";
                townsfolkReward = -50;
                mafiaReward = 31;
                sheriffReward = -29;
            }
            case "sheriff" -> {
                // will expand when the Sheriff Rating system will be in place
                hangingAnnouncement = "Oh poor confused Mafsvillians, what have you done? Why in the world did you have to execute your Sheriff PorkyPig " + target.getUser().getUsername() + "?!";
                townsfolkReward = -80;
                mafiaReward = 45;
                // not much point in setting anything for the Sheriff - he's been hung :)
            }
            default -> hangingAnnouncement = "The citizens just hanged an unknown creature not yet known to Mafsville";
        }
        game.addPublicMessage(hangingAnnouncement);
        // Identify all distinct role names still in play (case-insensitive)
        Set<String> rolesStillInGame = game.getPlayers().stream()
                .filter(PlayerInGame::isAlive)
                .map(p -> p.getRole().getRoleName().toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Fill reward map only for roles present
        for (String roleName : rolesStillInGame) {
            int amount = switch (roleName) {
                case "mafia" -> mafiaReward;
                case "townsfolk" -> townsfolkReward;
                case "sheriff" -> sheriffReward;
                default -> 0;
            };
            rewards.put(roleName, amount);
        }
        // Announcing now:
        StringBuilder sb = new StringBuilder("💰 Everyone in the Lynch mob receives rewards or penalties: ");
        rewards.forEach((role, amt) -> {
            sb.append(role).append(": ").append(amt).append("$, ");
        });
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        game.addPublicMessage(sb.toString());

        // Paying / deducting money from VOTERS' accounts
        List<PlayerInGame> lynchMob = game.getDayVotes().values().stream()
                .filter(v -> !v.isVoteForNight()
                        && v.getTargetId() != null
                        && target.getUser().getId().equals(v.getTargetId()))
                .map(v -> game.findPlayerById(v.getVoterId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        // 💰 Apply rewards to those who voted
        for (PlayerInGame voter : lynchMob) {
            Role role = voter.getRole();
            int amount = rewards.getOrDefault(role.getRoleName().toLowerCase(), 0);
            voter.setInGameMoney(voter.getInGameMoney() + amount);
        }

        lynchMob.forEach(v ->
                game.addLog("💰 " + v.getUser().getUsername() + " (" +
                        v.getRole().getRoleName() + ") " +
                        (rewards.getOrDefault(v.getRole().getRoleName().toLowerCase(), 0) >= 0 ? "earned" : "lost") +
                        " " + Math.abs(rewards.getOrDefault(v.getRole().getRoleName().toLowerCase(), 0)) + "$ for lynching.")
        );

        // --- Apply survival bonuses ---
        applyAndAnnounceDaySurvivalBonuses(game);

        // --- Check for victory conditions ---
        var winnerRuleOpt = victoryService.evaluate(game);
        if (winnerRuleOpt.isPresent()) {
            var rule = winnerRuleOpt.get();
            game.addPublicMessage(rule.getAnnouncement());
            game.addLog("Victory condition met: " + rule.getWinner());
            game.setFinished(true);
            stageData.put("winnerAlignment", rule.getWinner());
            stageData.put("winnerAnnouncement", rule.getAnnouncement());
            stageData.put("isDraw", rule.getWinner() == Alignment.NONE && rule.isDraw());
            stageData.put("allDead", rule.getWinner() == Alignment.NONE && rule.allDead());
            game.advanceStage(GamePhase.ENDED);
            return;
        }

        // --- Continue to contracts ---
        stageData.remove("lynchTarget");
        stageData.remove("lynchStartedAt");
        game.clearVotes();

        game.addPublicMessage("📜 The day draws to an end. Contracts phase begins.");
        game.advanceStage(GamePhase.CONTRACTS);
        game.addLog("Transitioned to CONTRACTS after lynching.");
    }

    private void handleHangingDefensePhase(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();

        // Initialize timer if needed
        if (!stageData.containsKey("phaseStartedAt")) {
            stageData.put("phaseStartedAt", LocalDateTime.now());
        }

        int defenseDuration = configSettingService.getIntSetting("DefenseDurationSeconds", 30);
        LocalDateTime startedAt = (LocalDateTime) stageData.get("phaseStartedAt");
        long elapsed = Duration.between(startedAt, LocalDateTime.now()).getSeconds();

        // Announce once when phase starts
        if (!stageData.containsKey("defenseAnnounced")) {
            game.getAccused().ifPresent(accused ->
                    game.addPublicMessage("⚖️ The trial begins! " + accused.getUser().getUsername() +
                            ", you have " + defenseDuration + " seconds to speak in your defense. " +
                            "Type '/end' to finish early.")
            );
            stageData.put("defenseAnnounced", true);
        }

        // Check if the accused ended their speech early (e.g. via endpoint)
        boolean endedEarly = Boolean.TRUE.equals(stageData.get("defenseEndedEarly"));

        // Proceed to verdict voting if time expired or ended early
        if (elapsed >= defenseDuration || endedEarly) {
            stageData.remove("defenseEndedEarly"); // cleanup
            stageData.put("phaseStartedAt", LocalDateTime.now());
            game.clearVerdictVotes(); // reset for new vote type
            game.advanceStage(GamePhase.HANGING_CONFIRMATION);
            game.addPublicMessage("🗳️ The defense has ended. All players, cast your votes: GUILTY or INNOCENT.");
            game.addLog("Defense phase ended, moved to HANGING_CONFIRMATION.");
        }
    }

    private void handleHangingConfirmationPhase(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();

        if (!stageData.containsKey("phaseStartedAt")) {
            stageData.put("phaseStartedAt", LocalDateTime.now());
        }

        int confirmationDuration = configSettingService.getIntSetting("VerdictVotingDurationSeconds", 45);
        LocalDateTime startedAt = (LocalDateTime) stageData.get("phaseStartedAt");
        long elapsed = Duration.between(startedAt, LocalDateTime.now()).getSeconds();

        // Get accused (must exist)
        Optional<PlayerInGame> accusedOpt = game.getAccused();
        if (accusedOpt.isEmpty()) {
            game.addLog("⚠️ Hanging confirmation reached with no accused — skipping to contracts.");
            game.advanceStage(GamePhase.CONTRACTS);
            return;
        }
        PlayerInGame accused = accusedOpt.get();

        // Get living voters (everyone except the accused)
        List<PlayerInGame> eligibleVoters = game.getPlayers().stream()
                .filter(PlayerInGame::isAlive)
                .filter(p -> !p.getUser().getId().equals(accused.getUser().getId()))
                .toList();

        // Check if all eligible players have already voted
        boolean allVoted = game.getVerdictVotes().size() >= eligibleVoters.size();

        // Wait until time expires or all votes are in
        if (elapsed < confirmationDuration && !allVoted) return;

        // --- Time to tally votes ---
        long guiltyVotes = game.getVerdictVotes().values().stream()
                .filter(v -> v.getChoice() == VerdictChoice.GUILTY)
                .count();
        long innocentVotes = game.getVerdictVotes().values().stream()
                .filter(v -> v.getChoice() == VerdictChoice.INNOCENT)
                .count();

        game.addPublicMessage("📜 Verdict voting has ended. " +
                "GUILTY: " + guiltyVotes + ", INNOCENT: " + innocentVotes);

        boolean guilty = guiltyVotes > innocentVotes;

        if (guilty) {
            // Grab a list of guilty voters
            List<PlayerInGame> guiltyVoters = game.getVerdictVotes().values().stream()
                    .filter(v -> v.getChoice() == VerdictChoice.GUILTY)
                    .map(v -> game.findPlayerById(v.getVoterId()).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();

            // --- Execute the accused ---
            gameManagerService.handlePlayerDeath(game, accused, 0L);
            game.addLog("Accused " + accused.getUser().getUsername() + " executed after guilty verdict.");
            String hangingAnnouncement = "";
            Map<String, Integer> rewards = new HashMap<>();
            int townsfolkReward = 0;
            int mafiaReward = 0;
            int sheriffReward = 0;
            switch (accused.getRole().getRoleName().toLowerCase()) {
                // Will add new role cases here as more roles arrive
                case "mafia" -> {
                    hangingAnnouncement = "For countless crimes against the town of Mafsville, the court has sentenced the Mafia member " + accused.getUser().getUsername() + " to death by hanging, which was executed without undue delay, making the streets of Mafsville just a little bit safer this evening. Citizens, rejoice!";
                    townsfolkReward = 45;
                    mafiaReward = -48;
                    sheriffReward = 34;
                }
                case "townsfolk" -> {
                    hangingAnnouncement = "Having completely lost their nerve due to all the chaos in town, the fair citizens of Mafsville have executed their fellow innocent Townsfolk " + accused.getUser().getUsername() + "! Well, justice is blind...";
                    townsfolkReward = -70;
                    mafiaReward = 31;
                    sheriffReward = -41;
                }
                case "sheriff" -> {
                    // will expand when the Sheriff Rating system will be in place
                    hangingAnnouncement = "Oh poor confused Mafsvillians, what have you done? Why in the world did you have to execute your Sheriff PorkyPig " + accused.getUser().getUsername() + "?!";
                    townsfolkReward = -95;
                    mafiaReward = 45;
                    sheriffReward = 0; // dead sheriff gets nothing
                }
                default -> hangingAnnouncement = "The citizens just hanged an unknown creature not yet known to Mafsville";
            }
            game.addPublicMessage(hangingAnnouncement);

            // Identify unique roles still present
            Set<String> rolesStillInGame = game.getPlayers().stream()
                    .filter(PlayerInGame::isAlive)
                    .map(p -> p.getRole().getRoleName().toLowerCase())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Fill reward map only for roles in play
            for (String roleName : rolesStillInGame) {
                int amount = switch (roleName) {
                    case "mafia" -> mafiaReward;
                    case "townsfolk" -> townsfolkReward;
                    case "sheriff" -> sheriffReward;
                    default -> 0;
                };
                rewards.put(roleName, amount);
            }

            // Announce
            StringBuilder sb = new StringBuilder("💰 Citizens receive their rewards or penalties: ");
            rewards.forEach((role, amt) -> {
                if (amt != 0) sb.append(role).append(": ").append(amt).append("$, ");
            });
            if (sb.length() > 1) sb.setLength(sb.length() - 2);
            game.addPublicMessage(sb.toString());

            // Apply only to those who voted GUILTY
            for (PlayerInGame voter : guiltyVoters) {
                Role role = voter.getRole();
                int amount = rewards.getOrDefault(role.getRoleName().toLowerCase(), 0);
                voter.setInGameMoney(voter.getInGameMoney() + amount);
                game.addLog("💰 " + voter.getUser().getUsername() + " (" + role.getRoleName() + ") "
                        + (amount >= 0 ? "earned" : "lost") + " " + Math.abs(amount)
                        + "$ for a guilty verdict on " + accused.getUser().getUsername());
            }
            applyAndAnnounceDaySurvivalBonuses(game);

        } else {
            // --- Acquitted ---
            game.addPublicMessage("🙏 The court found " + accused.getUser().getUsername() +
                    " innocent! The crowd disperses, uneasy but relieved.");
            game.addLog("Accused " + accused.getUser().getUsername() + " acquitted after trial.");
        }
        game.addLog("⚖️ Hanging verdict applied: " + accused.getUser().getUsername() +
                " (" + accused.getRole().getRoleName() + ") was " +
                (guilty ? "executed." : "acquitted."));

        // --- Step 5: Run victory check ---
        var winnerRuleOpt = victoryService.evaluate(game);
        if (winnerRuleOpt.isPresent()) {
            var rule = winnerRuleOpt.get();
            game.addPublicMessage(rule.getAnnouncement());
            game.addLog("Victory condition met after verdict: " + rule.getWinner());
            game.setFinished(true);
            stageData.put("winnerAlignment", rule.getWinner());
            stageData.put("winnerAnnouncement", rule.getAnnouncement());
            stageData.put("isDraw", rule.getWinner() == Alignment.NONE && rule.isDraw());
            stageData.put("allDead", rule.getWinner() == Alignment.NONE && rule.allDead());
            game.advanceStage(GamePhase.ENDED);
            return;
        }

        // --- Step 6: Move to CONTRACTS phase ---
        game.clearVerdictVotes();
        stageData.remove("phaseStartedAt");
        game.advanceStage(GamePhase.CONTRACTS);
        game.addPublicMessage("📜 The trial has concluded. Contracts phase begins.");
        game.addLog("Transitioned to CONTRACTS after hanging confirmation.");
    }

    private void handleContractsPhase(GameSessionRuntime game) {
        Map<String, Object> stageData = game.getStageData();

        // --- Step 1: Phase initialization ---
        LocalDateTime startedAt = (LocalDateTime) stageData.get("phaseStartedAt");
        if (startedAt == null) {
            stageData.put("phaseStartedAt", LocalDateTime.now());
            game.addPublicMessage("💼 The underworld awakens... Contracts are now being accepted.");
            game.addLog("Contracts phase started.");
        }

        // --- Step 2: Check if Hitman exists and is alive ---
        boolean hitmanAlive = game.getPlayers().stream()
                .anyMatch(p -> p.isAlive() && "HITMAN".equalsIgnoreCase(p.getRole().getRoleName()));

        if (!hitmanAlive) {
            game.addLog("No active Hitman found — skipping Contracts phase.");
//            game.addPublicMessage("💤 The Hitman is absent or dead. No contracts will be accepted today.");
            stageData.remove("contractOrders");
            game.advanceStage(GamePhase.NIGHT);
            game.addLog("Transitioned directly to NIGHT (no Hitman present).");
            return;
        }

        // --- Step 3: Wait for player input window ---
        int contractsDuration = configSettingService.getIntSetting("ContractsPhaseDurationSeconds", 40);
        long elapsed = Duration.between((LocalDateTime) stageData.get("phaseStartedAt"), LocalDateTime.now()).getSeconds();

        if (elapsed < contractsDuration) {
            // Still accepting contracts
            return;
        }

        // --- Step 4: Finalize orders and advance ---
        @SuppressWarnings("unchecked")
        List<ContractOrder> orders = (List<ContractOrder>) stageData.getOrDefault("contractOrders", new ArrayList<>());

        if (orders.isEmpty()) {
            game.addPublicMessage("☀️ No contracts were placed today. The town settles for the night.");
            game.addLog("Contracts phase ended — no orders recorded.");
        } else {
            game.addPublicMessage("🧾 Contracts phase closed. The underworld holds " +
                    orders.size() + " new order(s) for tonight...");
            game.addLog("Contracts phase ended with " + orders.size() + " active orders.");
        }

        // --- Step 5: Transition to NIGHT ---
//        stageData.remove("contractOrders"); // Optional: or keep if Hitman will read soon
        stageData.remove("phaseStartedAt");
        game.advanceStage(GamePhase.NIGHT);
        game.addPublicMessage("🌙 Night falls over Mafsville once more...");
        game.addLog("Transitioned to NIGHT after Contracts phase.");
    }

    public void applyAndAnnounceDaySurvivalBonuses(GameSessionRuntime game) {
        int mafBonus = configSettingService.getIntSetting("MafiaDaySurvivalBonus", 4);
        int neutralBonus = configSettingService.getIntSetting("NeutralDaySurvivalBonus", 3);

        // Store one entry per role name and their fixed bonus amount
        Map<String, Integer> bonusesByRole = new LinkedHashMap<>();
        List<PlayerInGame> players = game.getPlayers().stream().filter(PlayerInGame::isAlive).toList();
        for (PlayerInGame player : players) {
            if (!player.isAlive()) continue;

            int bonus = 0;
            Alignment align = player.getRole().getAlignment();

            if (align == Alignment.MAFIA) bonus = mafBonus;
            else if (align == Alignment.NEUTRAL) bonus = neutralBonus;

            if (bonus > 0) {
                player.setInGameMoney(player.getInGameMoney() + bonus);
                String roleName = player.getRole().getRoleName();

                // For multiple mafiosi, list once only
                if (roleName.equalsIgnoreCase("mafia")) roleName = "Mafia";

                bonusesByRole.putIfAbsent(roleName, bonus);
            }
        }

        // build public message
        StringBuilder sb = new StringBuilder("💰 Surviving villains get a small bonus: ");
        if (bonusesByRole.isEmpty()) {
            sb.append("(none)");
        } else {
            for (Map.Entry<String, Integer> e : bonusesByRole.entrySet()) {
                sb.append(e.getKey()).append(": $").append(e.getValue()).append(", ");
            }
            // trim trailing comma+space
            sb.setLength(sb.length() - 2);
        }
        game.addPublicMessage(sb.toString());
    }

    public void applyAndAnnounceHangingBonuses(GameSessionRuntime game) {
        int mafBonus = configSettingService.getIntSetting("MafiaDaySurvivalBonus", 4);
        int neutralBonus = configSettingService.getIntSetting("NeutralDaySurvivalBonus", 3);

        // Store one entry per role name and their fixed bonus amount
        Map<String, Integer> bonusesByRole = new LinkedHashMap<>();
        List<PlayerInGame> players = game.getPlayers().stream().filter(PlayerInGame::isAlive).toList();
        for (PlayerInGame player : players) {
            if (!player.isAlive()) continue;

            int bonus = 0;
            Alignment align = player.getRole().getAlignment();

            if (align == Alignment.MAFIA) bonus = mafBonus;
            else if (align == Alignment.NEUTRAL) bonus = neutralBonus;

            if (bonus > 0) {
                player.setInGameMoney(player.getInGameMoney() + bonus);
                String roleName = player.getRole().getRoleName();

                // For multiple mafiosi, list once only
                if (roleName.equalsIgnoreCase("mafia")) roleName = "Mafia";

                bonusesByRole.putIfAbsent(roleName, bonus);
            }
        }

        // build public message
        StringBuilder sb = new StringBuilder("💰 Surviving villains get a small bonus: ");
        if (bonusesByRole.isEmpty()) {
            sb.append("(none)");
        } else {
            for (Map.Entry<String, Integer> e : bonusesByRole.entrySet()) {
                sb.append(e.getKey()).append(": $").append(e.getValue()).append(", ");
            }
            // trim trailing comma+space
            sb.setLength(sb.length() - 2);
        }
        game.addPublicMessage(sb.toString());
    }

    private void handleGameEndedPhase(GameSessionRuntime game) {
        game.addLog("🐷 Handling endgame cleanup...");
        Map<String, Object> stageData = game.getStageData();

        // Only run once
        if (Boolean.TRUE.equals(stageData.get("cleanupDone"))) {
            return; // already processed
        }
        stageData.put("cleanupDone", true);

        // 1️⃣ Determine winner and alive players
        Alignment winner = (Alignment) game.getStageData().get("winnerAlignment");
        if (winner == null) {
            winner = Alignment.NONE; // prevent NPE
            game.getStageData().put("winnerAlignment", winner);
        }

        final Alignment finalWinner = winner; // <-- safe capture reference
        String announcement = (String) game.getStageData().get("winnerAnnouncement");
        Boolean isDraw = (Boolean) game.getStageData().getOrDefault("isDraw", false);
        Boolean allDead = (Boolean) game.getStageData().getOrDefault("allDead", false);

        if (finalWinner == Alignment.NONE) {
            if (Boolean.TRUE.equals(allDead)) {
                game.addPublicMessage("☠️ All perished in Mafsville. No victors this time.");
            } else if (Boolean.TRUE.equals(isDraw)) {
                game.addPublicMessage("🤝 It's a draw! Survivors get $25 consolation.");
                game.getPlayers().stream()
                        .filter(PlayerInGame::isAlive)
                        .forEach(p -> p.setInGameMoney(p.getInGameMoney() + 25));
            }
        }

        List<PlayerInGame> survivors = game.getPlayers().stream()
                .filter(PlayerInGame::isAlive)
                .toList();

        int baseBonus;

        switch (winner) {
            case MAFIA -> baseBonus = 120;
            case TOWNSFOLK -> baseBonus = 100;
            case NEUTRAL -> baseBonus = 200;
            case UNDEAD -> baseBonus = 150;
            default -> baseBonus = 0; // no winner
        }

        game.addPublicMessage("🏆 Victory for " + winner + "! Winning players get a bonus of $" + baseBonus + ".");
        game.addLog("Applying " + baseBonus + "$ base bonus to surviving members of " + winner);

        for (PlayerInGame p : survivors) {
            if (p.getRole().getAlignment() == winner) {
                p.setInGameMoney(p.getInGameMoney() + baseBonus);
            }
        }

        StringBuilder standings = new StringBuilder("📊 Final standings:\n");
        game.getPlayers().forEach(p -> {
            String mark = p.getRole().getAlignment() == finalWinner ? "🏅" : "💀";
            standings.append(mark)
                    .append(" ")
                    .append(p.getRole().getRoleName())
                    .append(" ")
                    .append(p.getUser().getUsername())
                    .append(" — $")
                    .append(p.getInGameMoney())
                    .append("\n");
        });
        game.addPublicMessage(standings.toString());

        for (PlayerInGame p : game.getPlayers()) {
            User user = p.getUser();
            user.setMoney(user.getMoney() + p.getInGameMoney());
            game.addLog("💰 Transferred " + p.getInGameMoney() +
                    "$ from " + p.getUser().getUsername() + " to global balance.");
        }

        // TODO: Compare per-role earnings and update leaderboard if exceeded
        // TODO: Implement a StatisticsService for this purpose

        gameHistoryService.archiveGame(game); // will write all logs + summary
        game.addLog("🗄️ Game archived successfully.");
//        gameManagerService.removeGame(game.getSessionId());
        game.getStageData().put("cleanupScheduledAt", LocalDateTime.now());
        game.addLog("🧹 Game " + game.getSessionId() + " removed from active sessions.");
        game.advanceStage(GamePhase.ENDED); // Ensure it's set clearly
        game.getStageData().put("cleanupDone", true);

    }

    private List<String> getAlivePlayerNames(GameSessionRuntime game) {
        return game.getPlayers().stream()
                .filter(PlayerInGame::isAlive)
                .map(p -> p.getUser().getUsername())
                .toList();
    }


}
