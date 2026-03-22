package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.dto.NightActionOptionDTO;
import com.mafia.mafia_backend.domain.dto.NightTargetsDTO;
import com.mafia.mafia_backend.domain.dto.TargetUserDTO;
import com.mafia.mafia_backend.domain.enums.ActionType;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.*;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.service.interfaces.ActionServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ActionService implements ActionServiceInterface {

    @Autowired
    private ConfigSettingService configSettingService;
    @Autowired
    private VictoryService victoryService;

    @Autowired
    private GameManagerService gameManagerService;

    @Override
    public void submitNightAction(GameSessionRuntime game, NightAction action) {
        game.addNightAction(action.getNightNumber(), action);
        Optional<PlayerInGame> playerInGameOptional = game.findPlayerById(action.getActorId());
        if (playerInGameOptional.isPresent()) {
            playerInGameOptional.get().setHasActedTonight(true);
        } else {
            throw new IllegalArgumentException("Player not found in the game");
        }
        game.addLog("Player " + action.getActorId() + " submitted action: " + action.getActionType()
                + " targeting " + action.getTargetId());
    }

    @Override
    public void cancelNightAction(GameSessionRuntime game, Long actorId, int nightNumber) {
        game.cancelNightAction(nightNumber, actorId);
        Optional<PlayerInGame> playerInGameOptional = game.findPlayerById(actorId);
        if (playerInGameOptional.isPresent()) {
            playerInGameOptional.get().setHasActedTonight(false);
        } else {
            throw new IllegalArgumentException("Player not found in the game");
        }
        game.addLog("Player " + actorId + " cancelled their night action.");
    }

    @Override
    public List<NightAction> getNightActions(GameSessionRuntime game, int nightNumber) {
        return game.getActionsForNight(nightNumber);
    }

    @Override
    public boolean allNightActionsComplete(GameSessionRuntime game, int nightNumber) {
        List<Long> expectedActors = getExpectedActors(game, nightNumber);
        List<NightAction> actions = game.getActionsForNight(nightNumber);
        for (Long actorId : expectedActors) {
            boolean acted = actions.stream()
                    .anyMatch(a -> a.getActorId().equals(actorId) && !a.isCancelled());
            if (!acted) {
                return false; // somebody still tardy
            }
        }
        return true; // all required actors have acted
    }

    @Override
    public void resolveNightActions(GameSessionRuntime game, int nightNumber) {
        game.addLog("Starting resolution for night " + nightNumber);

        List<NightAction> actions = game.getActionsForNight(nightNumber);
        List<Long> expectedActors = getExpectedActors(game, nightNumber);

        ResultStorage resultStorage = new ResultStorage();

        // --- SHERIFF TURN ---
        game.getPlayers().stream()
                .filter(p -> p.isAlive())
                .filter(p -> p.getRole().getRoleName().equalsIgnoreCase("sheriff"))
                .findFirst()
                .ifPresent(sheriff -> {
                    if (expectedActors.contains(sheriff.getUser().getId())) {
                        NightAction sheriffAction = actions.stream()
                                .filter(a -> a.getActorId().equals(sheriff.getUser().getId()))
                                .findFirst()
                                .orElse(null);

                        if (sheriffAction == null) {
                            processSheriffSkip(game, sheriff, resultStorage);
                        } else if (sheriffAction.getActionType() == NightActionType.CHECK) {
                            processSheriffCheck(game, sheriff, sheriffAction, resultStorage);
                        } else if (sheriffAction.getActionType() == NightActionType.KILL) {
                            processSheriffKill(game, sheriff, sheriffAction, resultStorage);
                        }
                    }
                });

        // --- MAFIA TURN ---
        Map<String, Object> stageData = game.getStageData();
        List<Long> mafiaOrder = (List<Long>) stageData.get("mafiaOrder");
        Integer currentIndex = (Integer) stageData.get("currentMafiaIndex");

        if (mafiaOrder != null && currentIndex != null && !mafiaOrder.isEmpty()) {
            Long activeMafiaId = mafiaOrder.get(currentIndex);
            game.findPlayerById(activeMafiaId).ifPresent(mafia -> {
                if (expectedActors.contains(activeMafiaId)) {
                    NightAction mafiaAction = actions.stream()
                            .filter(a -> a.getActorId().equals(activeMafiaId))
                            .findFirst()
                            .orElse(null);

                    if (mafiaAction == null) {
                        processMafiaSkip(game, mafia, resultStorage);
                    } else if (mafiaAction.getActionType() == NightActionType.KILL) {
                        processMafiaKill(game, mafia, mafiaAction, resultStorage);
                    }
                }
            });
        }

        // --- ANNOUNCEMENT PHASE placeholder ---
        announceNightResults(game, resultStorage);

        // --- VICTORY CHECK placeholder ---
        checkVictoryConditions(game);

        game.addLog("Resolution for night " + nightNumber + " complete.");
    }

    // Helper methods

    private List<Long> getExpectedActors(GameSessionRuntime game, int nightNumber) {
        List<Long> expected = new ArrayList<>();

        // Sheriff must act if alive
        game.getPlayers().stream()
                .filter(p -> p.isAlive())
                .filter(p -> p.getRole().getRoleName().equalsIgnoreCase("sheriff"))
                .findFirst()
                .ifPresent(sheriff -> expected.add(sheriff.getUser().getId()));

        // Mafia turn logic
        Map<String, Object> stageData = game.getStageData();
        List<Long> mafiaOrder = (List<Long>) stageData.get("mafiaOrder");
        Integer currentIndex = (Integer) stageData.get("currentMafiaIndex");

        if (mafiaOrder != null && currentIndex != null && !mafiaOrder.isEmpty()) {
            // Rotate through alive Mafia until we find one
            int attempts = 0;
            int index = currentIndex;

            while (attempts < mafiaOrder.size()) {
                Long mafiaId = mafiaOrder.get(index);
                boolean alive = game.findPlayerById(mafiaId).map(p -> p.isAlive()).orElse(false);

                if (alive) {
                    expected.add(mafiaId);
                    break;
                }

                // skip to next Mafia
                index = (index + 1) % mafiaOrder.size();
                attempts++;
            }
        }
        for (Long id : expected) {
            System.out.println("\uD83D\uDC37\uD83D\uDC37 Expecting action from player with ID " + id + " (" + game.findPlayerById(id).get().getUser().getUsername());
        }
        return expected;
    }

    private void processSheriffSkip(GameSessionRuntime game, PlayerInGame sheriff, ResultStorage storage) {
        ResultRecord record = new ResultRecord();
        record.setActorId(sheriff.getUser().getId());
        record.setActingRole("SHERIFF");
        record.setTargetId(null);
        record.setActionType(NightActionType.SKIP);
        record.setMoneyChange(-10); // penalty for skipping

        String message = "The Sheriff decided that the influx of Mafia into town was the perfect time for a trip to Vegas, " +
                "and slacked the night away, leaving the precinct unattended. " +
                "The Sheriff gets a penalty of " + record.getMoneyChange() + "$.";
        record.setPublicMessage(message);

        storage.getRecords().add(record);

        // Optional: if we already track skip counters
        sheriff.setSkipCount(sheriff.getSkipCount()+1);
        game.addLog("Sheriff " + sheriff.getUser().getUsername() + " skipped the night and was penalized " + record.getMoneyChange() + "$.");
    }

    private void processSheriffCheck(GameSessionRuntime game,
                                     PlayerInGame sheriff,
                                     NightAction action,
                                     ResultStorage storage) {

        PlayerInGame target = game.findPlayerById(action.getTargetId()).orElse(null);

        if (target == null) {
            game.addLog("Sheriff check failed: invalid or missing target.");
            return;
        }

        String targetRoleName = target.getRole().getRoleName();
        int reward = 0;

        // Determine monetary reward
        if (targetRoleName.equalsIgnoreCase("townsfolk")) {
            reward = 10;
        } else if (targetRoleName.equalsIgnoreCase("mafia")) {
            reward = 30;
        } else {
            reward = 0; // checking self
        }

        // Build the record
        ResultRecord record = new ResultRecord();
        record.setActorId(sheriff.getUser().getId());
        record.setActingRole("SHERIFF");
        record.setActionType(NightActionType.CHECK);
        record.setTargetId(target.getUser().getId());
        record.setMoneyChange(reward);
        record.setPublicMessage(
                "The Sheriff stayed up all night in the office, busy with detective work, " +
                        "and by morning, he knew exactly who " + target.getUser().getUsername() + " was! " +
                        "The Sheriff gets a bonus for proper casework."
        );

        storage.getRecords().add(record);

        // Log the private intelligence result (for now in logs)
        String privateInfo = "Intelligence has revealed that " + target.getUser().getUsername() +
                " is a " + targetRoleName + "!";
        game.addLog("[Office Intel for Sheriff " + sheriff.getUser().getUsername() + "]: " + privateInfo);

        // Apply reward directly
        sheriff.setInGameMoney(sheriff.getInGameMoney() + reward);
        game.addLog("Sheriff " + sheriff.getUser().getUsername() + " received +" + reward + "$ for investigation.");
    }

    private void processSheriffKill(GameSessionRuntime game,
                                    PlayerInGame sheriff,
                                    NightAction action,
                                    ResultStorage storage) {

        PlayerInGame target = game.findPlayerById(action.getTargetId()).orElse(null);

        if (target == null) {
            game.addLog("Sheriff kill failed: invalid or missing target.");
            return;
        }

        String targetRole = target.getRole().getRoleName();
        int moneyChange = 0;
        String message;

        if (targetRole.equalsIgnoreCase("mafia")) {
            moneyChange = 50; // big bonus
            message = "In an epic assault crowning a meticulously planned police operation, "
                    + "the Sheriff has eliminated the MAFIA man " + target.getUser().getUsername()
                    + "! The Sheriff gets a bonus for restoring justice: +" + moneyChange + "$";
        } else if (targetRole.equalsIgnoreCase("townsfolk")) {
            moneyChange = -40; // hefty penalty
            message = "Tonight, the Sheriff felt odd from the bottle of moonshine he confiscated earlier "
                    + "and missed during target practice, accidentally shooting the TOWNSFOLK "
                    + target.getUser().getUsername() + "! The Sheriff is fined for negligence: " + moneyChange + "$";
        } else if (targetRole.equalsIgnoreCase("sheriff") &&
                target.getUser().getId().equals(sheriff.getUser().getId())) {
            moneyChange = 0;
            message = "Haunted by the ghosts of Mafsville’s paperwork backlog, the Sheriff " + sheriff.getUser().getUsername() +  " turned his own gun, "
                    + "bringing an ironic end to his vigil.";
        } else {
            // future roles: neutral/unknown cases
            moneyChange = 0;
            message = "The Sheriff’s bullet found " + target.getUser().getUsername()
                    + ", a figure yet undefined in Mafsville’s lore.";
        }

        // Create result record
        ResultRecord record = new ResultRecord();
        record.setActorId(sheriff.getUser().getId());
        record.setActingRole("SHERIFF");
        record.setActionType(NightActionType.KILL);
        record.setTargetId(target.getUser().getId());
        record.setMoneyChange(moneyChange);
        record.setPublicMessage(message);

        storage.getRecords().add(record);

        // Apply money effect immediately
        sheriff.setInGameMoney(sheriff.getInGameMoney() + moneyChange);

        game.addLog("Sheriff " + sheriff.getUser().getUsername() +
                " performed a KILL on " + target.getUser().getUsername() +
                " (" + targetRole + "), money change: " + moneyChange + "$.");
    }

    private void processMafiaSkip(GameSessionRuntime game,
                                  PlayerInGame mafia,
                                  ResultStorage storage) {

        ResultRecord record = new ResultRecord();
        record.setActorId(mafia.getUser().getId());
        record.setActingRole("MAFIA");
        record.setTargetId(null);
        record.setActionType(NightActionType.SKIP);
        record.setMoneyChange(-10); // penalty for skipping
        record.setPublicMessage(
                "The Mafia soldier responsible for tonight's operation spent many hours at the mirror, "
                        + "trying to find a hat that matched both his shoes and his Thompson gun. "
                        + "By the time the perfect hat was chosen, it was already morning... "
                        + "The Mafia soldier gets a penalty of " + record.getMoneyChange() + "$."
        );

        storage.getRecords().add(record);

        mafia.setSkipCount(mafia.getSkipCount() + 1);
        mafia.setInGameMoney(mafia.getInGameMoney() + record.getMoneyChange());

        game.addLog("Mafia " + mafia.getUser().getUsername()
                + " skipped the night and was penalized " + record.getMoneyChange() + "$.");
    }

    private void processMafiaKill(GameSessionRuntime game,
                                  PlayerInGame mafia,
                                  NightAction action,
                                  ResultStorage storage) {

        PlayerInGame target = game.findPlayerById(action.getTargetId()).orElse(null);

        if (target == null) {
            game.addLog("Mafia kill failed: invalid or missing target.");
            return;
        }

        String targetRole = target.getRole().getRoleName().toLowerCase();
        int moneyChange = 0;
        String message;

        if (targetRole.equals("townsfolk")) {
            moneyChange = 20; // small reward for standard hit
            message = "Relentless in their attempts to terrorize Mafsville into submission, "
                    + "tonight the Mafia ruthlessly slew the harmless TOWNSFOLK "
                    + target.getUser().getUsername()
                    + "! The Mafia earns a blood bonus: +" + moneyChange + "$.";
        } else if (targetRole.equals("sheriff")) {
            moneyChange = 50; // major reward
            message = "An ominous black car was dispatched this night from the Mafia's garage... "
                    + "Woe be to Mafsville — the Mafia managed to eliminate the local SHERIFF "
                    + target.getUser().getUsername()
                    + "! The Mafia earns a major bonus: +" + moneyChange + "$.";
        } else if (target.getUser().getId().equals(mafia.getUser().getId())) {
            moneyChange = 0;
            message = "In a tragic mix-up of shadows and mirrors, "
                    + "the Mafia soldier " + mafia.getUser().getUsername() + " mistook his own reflection for an enemy and fired. "
                    + "No reward, no penalty — just shame.";
        } else {
            moneyChange = 10; // default for unclassified roles
            message = "The Mafia's bullet found " + target.getUser().getUsername()
                    + ", whose role remains a mystery in Mafsville’s underworld. "
                    + "The Mafia gains a modest bonus: +" + moneyChange + "$.";
        }

        // Create result record
        ResultRecord record = new ResultRecord();
        record.setActorId(mafia.getUser().getId());
        record.setActingRole("MAFIA");
        record.setActionType(NightActionType.KILL);
        record.setTargetId(target.getUser().getId());
        record.setMoneyChange(moneyChange);
        record.setPublicMessage(message);

        storage.getRecords().add(record);

        // Apply money gain immediately
        mafia.setInGameMoney(mafia.getInGameMoney() + moneyChange);

        game.addLog("Mafia " + mafia.getUser().getUsername()
                + " performed a KILL on " + target.getUser().getUsername()
                + " (" + targetRole + "), money change: " + moneyChange + "$.");
    }


    private void announceNightResults(GameSessionRuntime game, ResultStorage storage) {
        List<ResultRecord> records = storage.getRecords();

        if (records.isEmpty()) {
            game.addLog("No results to announce this morning.");
            return;
        }

        int delaySeconds = 3; // later configurable (ConfigSettingService)
        game.addPublicMessage("The sun rises. Night is over.");

        for (int i = 0; i < records.size(); i++) {
            ResultRecord record = records.get(i);

            // --- 1️⃣ Dramatic pause (conceptual placeholder) ---
            game.addLog("Waiting " + delaySeconds + " seconds before next announcement...");
            // Real-time wait would happen in scheduler / frontend later

            // --- 2️⃣ Public announcement ---
            game.addPublicMessage(record.getPublicMessage());
            game.addLog("Announcement displayed: " + record.getPublicMessage());

            // --- 3️⃣ Apply in-game effects ---
            switch (record.getActionType()) {
                case SKIP -> applySkipEffect(game, record);
                case CHECK -> applyCheckEffect(game, record);
                case KILL -> applyKillEffect(game, record);
                default -> game.addLog("Unknown record type: " + record.getActionType());
            }

            // Another dramatic log pause before next record
            game.addLog("Processed record " + (i + 1) + "/" + records.size());
        }

        game.addLog("All nightly results have been announced.");
    }

    private void checkVictoryConditions(GameSessionRuntime game) {
        Optional<VictoryService.VictoryRule> winnerRule = victoryService.evaluate(game);

        if (winnerRule.isPresent()) {
            VictoryService.VictoryRule rule = winnerRule.get();

            game.addPublicMessage(rule.getAnnouncement());
            game.addLog("Victory condition met: " + rule.getWinner());

            if (rule.getWinner() == Alignment.NONE) {
                // Draw or “everyone died” case
                if (rule.getAnnouncement().equals("The game is a draw! Well... everyone could have fared better this time.")) {
                    game.addPublicMessage("🤝 " + rule.getAnnouncement() + " Survivors get a small bonus of $25");
                    for (PlayerInGame player : game.getPlayers()) {
                        if (player.isAlive()) {
                            player.setInGameMoney(player.getInGameMoney() + 25); // small draw game bonus
                        }
                    }
                }
                else {
                    game.addPublicMessage("❌❌❌ " + rule.getAnnouncement()); // everyone died
                }
                game.advanceStage(GamePhase.ENDED);
                game.setFinished(true);
            } else if (rule.getWinner() == Alignment.TOWNSFOLK) {
                game.addPublicMessage("🌞 The Townsfolk have prevailed!");
                game.advanceStage(GamePhase.ENDED);
                game.setFinished(true);
            } else if (rule.getWinner() == Alignment.MAFIA) {
                game.addPublicMessage("💀 The Mafia rule the streets of Mafsville!");
                game.advanceStage(GamePhase.ENDED);
                game.setFinished(true);
            } else {
                // Neutral or unhandled faction victory
                game.addPublicMessage("⚖️ The balance of power shifts — " + rule.getWinner() + " claim victory!");
                game.advanceStage(GamePhase.ENDED);
                game.setFinished(true);
            }

            // For clarity, reset phase data
            game.getStageData().clear();

        } else {
            // No victory yet — continue to next phase
            game.addLog("No victory or draw detected — proceeding to next phase.");
            game.advanceStage(GamePhase.DAY_VOTING);
            game.addLog("Game transitioned to DAY_VOTING phase.");
        }
    }

    private void applySkipEffect(GameSessionRuntime game, ResultRecord record) {
        if (record.getActorId() == null) return;

        game.findPlayerById(record.getActorId()).ifPresent(player -> {
            // money/penalty for skip was already applied in processor; here we only
            // handle side effects like auto-eliminate on repeated skips (if desired).
            if (player.getSkipCount() >= 2) {
                int penalty = configSettingService.getIntSetting("PenaltyMoneyForNoResponse", 50);
                player.setInGameMoney(player.getInGameMoney() - penalty);
                player.setAlive(false);
                game.addLog("Player " + player.getUser().getUsername()
                        + " eliminated due to repeated skips. Extra penalty: -" + penalty + "$.");
            }
        });
    }

    private void applyCheckEffect(GameSessionRuntime game, ResultRecord record) {
        // Rewards were already applied in processor. Private intel was logged there too.
        // Nothing else to mutate here for now.
        game.addLog("Check effect applied for actorId=" + record.getActorId()
                + " targetId=" + record.getTargetId());
    }

    private void applyKillEffect(GameSessionRuntime game, ResultRecord record) {
        if (record.getTargetId() == null) {
            game.addLog("applyKillEffect called with null target — skipping.");
            return;
        }

        Long killerId = record.getActorId() != null ? record.getActorId() : 0L;

        game.findPlayerById(record.getTargetId()).ifPresent(target -> {
            if (!target.isAlive()) {
                game.addLog("Target " + target.getUser().getUsername() + " already dead — skipping duplicate kill.");
                return;
            }

            gameManagerService.handlePlayerDeath(game, target, killerId);
            game.addLog("☠️ applyKillEffect executed: " + target.getUser().getUsername() + " killed by " + killerId);
        });
    }

    /**
     * Compute available actions for the given player in this game session.
     */
    @Override
    public NightActionCatalogDTO computeActionsFor(GameSessionRuntime game, PlayerInGame player) {

        // === Example action pool by role (temporary hardcoded logic) ===
        List<NightActionOptionDTO> actions = new ArrayList<>();

        String roleName = player.getRole() != null
                ? player.getRole().getRoleName().toLowerCase()
                : "townsfolk";

        switch (roleName) {
            case "mafia" -> {
                actions.add(new NightActionOptionDTO(
                        "KILL", "Kill", ActionType.TARGET_PLAYER,
                        1, 0, null, true, null
                ));
            }
            case "sheriff" -> {
                actions.add(new NightActionOptionDTO(
                        "CHECK", "Check", ActionType.TARGET_PLAYER,
                        1, 0, null, true, null
                ));
                actions.add(new NightActionOptionDTO(
                        "KILL", "Kill", ActionType.TARGET_PLAYER,
                        1, 0, null, true, null
                ));
            }
            default -> {
                // Townsfolk, no night actions
            }
        }

        // === Build target lists ===
        List<PlayerInGame> alive = game.getPlayers().stream()
                .filter(PlayerInGame::isAlive)
                .toList();

        List<PlayerInGame> dead = game.getPlayers().stream()
                .filter(p -> !p.isAlive())
                .toList();

        List<TargetUserDTO> aliveTargets = alive.stream()
                .map(p -> new TargetUserDTO(p.getUser().getId(), p.getUser().getUsername()))
                .collect(Collectors.toList());

        List<TargetUserDTO> deadTargets = dead.stream()
                .map(p -> new TargetUserDTO(p.getUser().getId(), p.getUser().getUsername()))
                .collect(Collectors.toList());

        // Role list (used by e.g. KILL_ROLE)
        List<String> roles = game.getPlayers().stream()
                .map(p -> p.getRole() != null ? p.getRole().getRoleName() : "Unknown")
                .distinct()
                .collect(Collectors.toList());

        NightTargetsDTO targets = new NightTargetsDTO(aliveTargets, deadTargets, roles);

        // === Compute remaining seconds in the current night phase ===
        LocalDateTime startedAt = (LocalDateTime) game.getStageData().get("phaseStartedAt");
        long secondsLeft = 0;
        if (startedAt != null) {
            int nightDuration = 180;
            secondsLeft = Math.max(0,
                    nightDuration - Duration.between(startedAt, LocalDateTime.now()).getSeconds());
        }

        return new NightActionCatalogDTO(actions, targets, secondsLeft);
    }
}
