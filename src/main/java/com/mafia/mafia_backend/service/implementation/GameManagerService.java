package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.controller.ContractsController;
import com.mafia.mafia_backend.domain.entity.*;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.game.GameRegistry;
import com.mafia.mafia_backend.service.interfaces.GameManagerServiceInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameManagerService implements GameManagerServiceInterface {
    private final ConfigSettingRepository configSettingRepository;
    private final RoleRepository roleRepository;
    @Getter
    private final List<GameSessionRuntime> activeGames = new ArrayList<>();
    private final UserService userService;
    private final ConfigSettingService configSettingService;
    private final RoleRefusalTrackerRepository roleRefusalTrackerRepository;
    @Autowired
    private PrivateMessagingService privateMessagingService;
    private final GameRegistry gameRegistry;

//    public GameManagerService(ConfigSettingRepository configSettingRepository, RoleRepository roleRepository, UserService userService, ConfigSettingService configSettingService, RoleRefusalTrackerRepository roleRefusalTrackerRepository) {
//        this.configSettingRepository = configSettingRepository;
//        this.roleRepository = roleRepository;
//        this.userService = userService;
//        this.configSettingService = configSettingService;
//        this.roleRefusalTrackerRepository = roleRefusalTrackerRepository;
//    }

    public Optional<GameSessionRuntime> findSessionByUuid(UUID sessionId) {
        return activeGames.stream()
                .filter(g -> g.getSessionId().equals(sessionId))
                .findFirst();
    }

    public GameSessionRuntime findByGameId(Long gameId) {
        return activeGames.stream()
                .filter(s -> s.getGame() != null && gameId.equals(s.getGame().getId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public GameSessionRuntime startNewGame() {
        GameSessionRuntime session = new GameSessionRuntime(configSettingService);

        Game game = new Game();
        game.setPhase(GamePhase.LOBBY);
        game.setCreatedAt(LocalDateTime.now());

        session.setGame(game);
        activeGames.add(session);

        // 🐖 Register in the global registry
        gameRegistry.registerGame(game);

        return session;
    }

    @Override
    public boolean joinGame(GameSessionRuntime game, User user) {
        // Step 1: Prevent joining twice
        boolean alreadyJoined = game.getPlayers().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId()));

        if (alreadyJoined) {
            return false; // User already in this game
        }


        // Step 2: Create PlayerInGame
        PlayerInGame piglet = new PlayerInGame();
        piglet.setUser(user);
        piglet.setJoinedAt(LocalDateTime.now());
        piglet.setAlive(true); // Default state at start

        // Step 3: Add to game
        game.getPlayers().add(piglet);

        // Step 4: Log it
        game.addLog("Player " + user.getUsername() + " joined the game.");

        return true;
    }

    @Override
    public Optional<GameSessionRuntime> findSessionById(Long gameId) {
        return activeGames.stream()
                .filter(session -> session.getGame().getId().equals(gameId))
                .findFirst();
    }

    @Override
    public void advancePhase(GameSessionRuntime game, GamePhase newPhase) { /* ... */ }

    @Override
    public Optional<GameSessionRuntime> findAvailableGame() { return Optional.empty(); }

    @Override
    public int getLobbyDurationFromSettings() {
        return configSettingRepository.findByName("LobbyDurationSeconds")
                .map(ConfigSetting::getIntvalue)
                .orElse(180); // TODO: Change to 600 before deployment
    }

    @Override
    public int getMaxPlayersFromSettings() {
        return configSettingRepository.findByName("MaxPlayersPerGame")
                .map(ConfigSetting::getIntvalue)
                .orElse(50); // Default
    }

    @Override
    public String pickRandomIntroMessage() {
        List<String> messages = List.of(
                "The game begins – let's play Mafia!",
                "Mafia has come to town – time to play!",
                "Reach for your guns and brace yourselves – the game commences!"
        );
        return messages.get(new Random().nextInt(messages.size()));
    }

    @Override
    public void offerRolesToPlayers(GameSessionRuntime game, List<Role> rolesToDistribute) {
        List<PlayerInGame> availablePlayers = new ArrayList<>(game.getPlayers());
        Collections.shuffle(availablePlayers);

        for (Role role : rolesToDistribute) {
            if (role.getRoleName().equalsIgnoreCase("Townsfolk")) continue;
            if (availablePlayers.isEmpty()) {
                log.warn("No available players left to assign role: " + role.getRoleName());
                break;
            }

            PlayerInGame chosenOne = availablePlayers.remove(0);
            chosenOne.setRole(role);
            chosenOne.setAlignment(role.getAlignment());

            String username = chosenOne.getUser().getUsername();
            Long playerId = chosenOne.getUser().getId();

            String message = switch (role.getRoleName().toLowerCase()) {
                case "mafia" -> "💼 " + username + ", do you crave luxury, power, and a life in the shadows? "
                        + "The Mafia has extended you an invitation. Accept or refuse — the town’s fate may depend on it.";
                case "sheriff" -> "🔫 " + username + ", long nights, little pay, and endless danger — "
                        + "but Mafsville needs a Sheriff. Will you uphold the law, or decline the badge?";
                default -> "📜 " + username + ", you’ve been offered the role of " + role.getRoleName() + ". Accept or decline?";
            };
            privateMessagingService.sendPrivateMessage(playerId, "[OFFER_ROLE]" + message);

            // Per-player pending key
            String pendingKey = "pending_" + role.getRoleName() + "_" + playerId;
            game.getStageData().put(pendingKey, playerId);
            game.getStageData().put(pendingKey + "_timestamp", LocalDateTime.now());

            game.addLog("The role of " + role.getRoleName() + " has been offered to " + username + ".");
        }
    }

    private List<Role> fetchRolesForGame(int playerCount) {
        List<String> roleNames;

        if (playerCount == 4) {
            roleNames = List.of("Mafia", "Sheriff", "Townsfolk", "Townsfolk");
        } else {
            // For now, just throw — later we’ll expand logic here
            throw new IllegalStateException("Only 4-player games supported at this stage.");
        }

        List<Role> selectedRoles = new ArrayList<>();
        for (String roleName : roleNames) {
            Optional<Role> roleOpt = roleRepository.findByRoleName(roleName);
            if (roleOpt.isEmpty()) {
                throw new IllegalStateException("Missing role in DB: " + roleName);
            }
            selectedRoles.add(roleOpt.get());
        }

        return selectedRoles;
    }


    @Override
    public void assignInitialRoles(GameSessionRuntime game) {
        int playerCount = game.getPlayers().size();

        // Fetch roles based on player count (for now, hardcoded to 4-player roles)
        List<Role> selectedRoles = fetchRolesForGame(playerCount);

        // Offer roles to random players
        offerRolesToPlayers(game, selectedRoles);

        // --- 🧠 Initialize Mafia rotation order (for future multi-mafia support) ---
        List<Long> mafiaOrder = game.getPlayers().stream()
                .filter(p -> p.getRole() != null)
                .filter(p -> p.getRole().getRoleName().equalsIgnoreCase("mafia"))
                .map(p -> p.getUser().getId())
                .collect(Collectors.toList());

        if (!mafiaOrder.isEmpty()) {
            Collections.shuffle(mafiaOrder); // randomize who goes first
            game.getStageData().put("mafiaOrder", mafiaOrder);
            game.getStageData().put("currentMafiaIndex", 0);
            game.addLog("Mafia order initialized: " + mafiaOrder);
        }

        // Log the distribution summary in public chat
        String summary = selectedRoles.stream()
                .collect(Collectors.groupingBy(Role::getRoleName, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> entry.getValue() + " × " + entry.getKey())
                .collect(Collectors.joining(", "));

        game.addLog("There are " + playerCount + " players in the game. Roles to be assigned: " + summary);
    }

    @Override
    public String confirmOfferedRole(Long gameId, Long userId) {
        GameSessionRuntime game = findSessionById(gameId)
                .orElseThrow(() -> new IllegalStateException("Game not found"));

        if (game.getStage() != GamePhase.ROLE_ASSIGNMENT)
            throw new IllegalStateException("Game is not in role assignment phase");

        PlayerInGame player = game.findPlayerByUsername(
                        userService.getUserById(userId).getUsername())
                .orElseThrow(() -> new IllegalStateException("Player not part of this game"));

        Map<String, Object> stageData = game.getStageData();

        // find this player's pending key
        String pendingKey = stageData.keySet().stream()
                .filter(k -> k.startsWith("pending_") && k.endsWith("_" + userId))
                .findFirst()
                .orElse(null);

        if (pendingKey == null)
            throw new IllegalStateException("Player has no pending role offer");

        stageData.remove(pendingKey);
        stageData.remove(pendingKey + "_timestamp");

        Role confirmedRole = player.getRole();
        if (confirmedRole != null) {
            resetRefusalCount(userId, confirmedRole.getRoleName());
            switch (confirmedRole.getRoleName().toLowerCase()) {
                case "mafia" -> game.addPublicMessage("Citizens, beware! Yet another Mafia member has come to town, bringing chaos and terror!");
                case "sheriff" -> game.addPublicMessage("Fear, evil-doers! There is a new Sheriff in town - with guns loaded and gum being chewed!");
            }
            String confirmMsg = switch (confirmedRole.getRoleName().toLowerCase()) {
                case "mafia" -> "💼 You have accepted the role of Mafia. Keep your identity secret — the night will soon begin...";
                case "sheriff" -> "🔫 You have accepted the role of Sheriff. Stay vigilant, the town depends on you!";
                default -> "📜 You have accepted the role of " + confirmedRole.getRoleName() + ". Prepare for the game ahead!";
            };
            privateMessagingService.sendPrivateMessage(userId, confirmMsg);
        }

        // check if all roles assigned
        boolean pendingExists = stageData.keySet().stream()
                .anyMatch(k -> k.startsWith("pending_"));
        if (!pendingExists) {
            assignTownsfolkToRemainingPlayers(game);
            game.addPublicMessage("All roles accepted! The game begins!");
            System.out.println("🐽 All roles accepted! Advancing " + game.getGame().getId() + " to NIGHT phase.");
            game.advanceStage(GamePhase.NIGHT);
            game.addLog("Game moved to NIGHT phase after all roles confirmed.");
        }

        assert confirmedRole != null;
        return confirmedRole.getRoleName().toLowerCase();
    }

    public boolean canRefuseRole(Long userId, String roleName) {
        RoleRefusalTracker tracker = userService.getOrCreateTracker(userId);
        int limit = configSettingService.getIntSetting("MaxRefusalsPerRole", 2);

        return switch (roleName.toLowerCase()) {
            case "sheriff" -> tracker.getSheriffRefusedTimes() < limit;
            case "bum" -> tracker.getBumRefusedTimes() < limit;
            case "doctor" -> tracker.getDoctorRefusedTimes() < limit;
            case "schizophrenic" -> tracker.getSchizRefusedTimes() < limit;
            case "mafia" -> tracker.getMafiaRefusedTimes() < limit;
            case "lawyer" -> tracker.getLawyerRefusedTimes() < limit;
            case "reporter" -> tracker.getReporterRefusedTimes() < limit;
            case "agent" -> tracker.getAgentRefusedTimes() < limit;
            case "maniac" -> tracker.getManiacRefusedTimes() < limit;
            case "hitman" -> tracker.getHitmanRefusedTimes() < limit;
            case "broad" -> tracker.getBroadRefusedTimes() < limit;
            case "hacker" -> tracker.getHackerRefusedTimes() < limit;
            case "bodyguard" -> tracker.getBodyguardRefusedTimes() < limit;
            case "necromancer" -> tracker.getNecroRefusedTimes() < limit;
            default -> true;
        };
    }

    @Override
    public void offerRoleToNextAvailablePlayer(GameSessionRuntime game, Role offeredRole) {
        List<PlayerInGame> potentialRecipients = game.getPlayers().stream()
                .filter(p -> {
                    Role role = p.getRole();
                    return role == null || "Townsfolk".equalsIgnoreCase(role.getRoleName());
                })
                .collect(Collectors.toList());

        if (potentialRecipients.isEmpty()) {
            game.addLog("No players left to offer role: " + offeredRole.getRoleName());
            game.addPublicMessage("No available players to offer the role of " + offeredRole.getRoleName() + ".");
            return;
        }

        Collections.shuffle(potentialRecipients);
        PlayerInGame chosen = potentialRecipients.get(0);

        chosen.setRole(offeredRole);
        chosen.setAlignment(offeredRole.getAlignment());

        // Create new pending key
        Long playerId = chosen.getUser().getId();
        String pendingKey = "pending_" + offeredRole.getRoleName() + "_" + playerId;
        game.getStageData().put(pendingKey, playerId);
        game.getStageData().put(pendingKey + "_timestamp", LocalDateTime.now());
        game.getStageData().put("phaseStartedAt", LocalDateTime.now()); // reset global timer

        String username = chosen.getUser().getUsername();
        String message = switch (offeredRole.getRoleName().toLowerCase()) {
            case "mafia" -> "💼 " + username + ", do you crave luxury, power, and a life in the shadows? "
                    + "The Mafia has extended you an invitation. Accept or refuse — the town’s fate may depend on it.";
            case "sheriff" -> "🔫 " + username + ", long nights, little pay, and endless danger — "
                    + "but Mafsville needs a Sheriff. Will you uphold the law, or decline the badge?";
            default -> "📜 " + username + ", you’ve been offered the role of " + offeredRole.getRoleName() + ". Accept or decline?";
        };
        privateMessagingService.sendPrivateMessage(playerId, "[OFFER_ROLE]" + message);

        game.addLog("Role of " + offeredRole.getRoleName() + " re-offered to " + username + ".");
    }

    @Override
    public void refuseOfferedRole(Long gameId, Long userId) {
        GameSessionRuntime game = findSessionById(gameId)
                .orElseThrow(() -> new IllegalStateException("Game not found"));

        if (game.getStage() != GamePhase.ROLE_ASSIGNMENT)
            throw new IllegalStateException("Game is not in role assignment phase");

        User user = userService.getUserById(userId);
        PlayerInGame player = game.findPlayerByUsername(user.getUsername())
                .orElseThrow(() -> new IllegalStateException("Player not in game"));

        Role offeredRole = player.getRole();
        if (offeredRole == null)
            throw new IllegalStateException("No role has been offered to this player");

        Map<String, Object> stageData = game.getStageData();
        String pendingKey = "pending_" + offeredRole.getRoleName() + "_" + userId;

        if (!stageData.containsKey(pendingKey))
            throw new IllegalStateException("Player is not pending confirmation for this role");

        // refusal limits
        if (!canRefuseRole(user.getId(), offeredRole.getRoleName()))
            throw new IllegalStateException("You are not allowed to refuse the role of " + offeredRole.getRoleName() + " anymore.");
        if (player.getRefusalsUsed() >= 1)
            throw new IllegalStateException("You have already used your refusal opportunity.");

        player.incrementRefusalsUsed();
        incrementRefusalCount(userId, offeredRole.getRoleName());

        String refusalMsg = switch (offeredRole.getRoleName().toLowerCase()) {
            case "mafia" -> "🚫 You refused the Mafia offer. A risky choice... The Don won’t forget this.";
            case "sheriff" -> "🚫 You turned down the Sheriff badge. The town will have to find another protector.";
            default -> "🚫 You refused the offer of " + offeredRole.getRoleName() + ". You remain a Townsfolk for now.";
        };
        privateMessagingService.sendPrivateMessage(userId, refusalMsg);

        stageData.remove(pendingKey);
        stageData.remove(pendingKey + "_timestamp");

        player.setRole(null);
        player.setAlignment(null);

        offerRoleToNextAvailablePlayer(game, offeredRole);
    }

    @Override
    public void banishIdlePlayers(GameSessionRuntime game) {
        if (game.getStage() != GamePhase.ROLE_ASSIGNMENT) return;

        int timeoutSeconds = configSettingService.getIntSetting("RoleAssignmentTimeoutSeconds", 60);
        LocalDateTime now = LocalDateTime.now();

        List<String> keysToRemove = new ArrayList<>();

        for (String key : new ArrayList<>(game.getStageData().keySet())) {
            if (key.startsWith("pending_") && !key.endsWith("_timestamp")) {
                Long userId = (Long) game.getStageData().get(key);
                LocalDateTime offeredAt = (LocalDateTime) game.getStageData().get(key + "_timestamp");

                if (offeredAt == null || Duration.between(offeredAt, now).getSeconds() >= timeoutSeconds) {
                    PlayerInGame player = game.findPlayerById(userId).orElse(null);
                    if (player == null) continue;

                    String roleName = player.getRole() != null ? player.getRole().getRoleName() : "Unknown";
                    game.getPlayers().remove(player);
                    game.addPublicMessage(player.getUser().getUsername() + " failed to accept the role of " + roleName + " in time and was removed from the game.");
                    game.addLog("Player " + player.getUser().getUsername() + " was banished due to inactivity during role assignment.");

                    int fine = configSettingService.getIntSetting("PenaltyMoneyForNoResponse", 100);
                    userService.modifyMoney(player.getUser().getId(), -fine);

                    keysToRemove.add(key);
                    keysToRemove.add(key + "_timestamp");
                }
            }
        }

        keysToRemove.forEach(game.getStageData()::remove);

        if (game.getPlayers().size() < 4) {
            game.advanceStage(GamePhase.CANCELED);
            game.setAborted(true);
            game.addPublicMessage("Too many players have left. Game canceled.");
            game.addLog("Game canceled due to insufficient players after idle banishments.");
            game.getPlayers().clear();
            game.getStageData().put("cleanupScheduledAt", LocalDateTime.now());
        }
    }

    public void incrementRefusalCount(Long userId, String roleName) {
        RoleRefusalTracker tracker = userService.getOrCreateTracker(userId);

        switch (roleName.toLowerCase()) {
            case "sheriff" -> tracker.setSheriffRefusedTimes(tracker.getSheriffRefusedTimes() + 1);
            case "bum" -> tracker.setBumRefusedTimes(tracker.getBumRefusedTimes() + 1);
            case "doctor" -> tracker.setDoctorRefusedTimes(tracker.getDoctorRefusedTimes() + 1);
            case "schizophrenic" -> tracker.setSchizRefusedTimes(tracker.getSchizRefusedTimes() + 1);
            case "mafia" -> tracker.setMafiaRefusedTimes(tracker.getMafiaRefusedTimes() + 1);
            case "lawyer" -> tracker.setLawyerRefusedTimes(tracker.getLawyerRefusedTimes() + 1);
            case "reporter" -> tracker.setReporterRefusedTimes(tracker.getReporterRefusedTimes() + 1);
            case "agent" -> tracker.setAgentRefusedTimes(tracker.getAgentRefusedTimes() + 1);
            case "maniac" -> tracker.setManiacRefusedTimes(tracker.getManiacRefusedTimes() + 1);
            case "hitman" -> tracker.setHitmanRefusedTimes(tracker.getHitmanRefusedTimes() + 1);
            case "broad" -> tracker.setBroadRefusedTimes(tracker.getBroadRefusedTimes() + 1);
            case "hacker" -> tracker.setHackerRefusedTimes(tracker.getHackerRefusedTimes() + 1);
            case "bodyguard" -> tracker.setBodyguardRefusedTimes(tracker.getBodyguardRefusedTimes() + 1);
            case "necromancer" -> tracker.setNecroRefusedTimes(tracker.getNecroRefusedTimes() + 1);

            default -> {
                return; // we ignore Townsfolk and special roles
            }
        }

        roleRefusalTrackerRepository.save(tracker);
    }

    public void resetRefusalCount(Long userId, String roleName) {
        RoleRefusalTracker tracker = userService.getOrCreateTracker(userId);

        switch (roleName.toLowerCase()) {
            case "sheriff" -> tracker.setSheriffRefusedTimes(0);
            case "bum" -> tracker.setBumRefusedTimes(0);
            case "doctor" -> tracker.setDoctorRefusedTimes(0);
            case "schizophrenic" -> tracker.setSchizRefusedTimes(0);
            case "mafia" -> tracker.setMafiaRefusedTimes(0);
            case "lawyer" -> tracker.setLawyerRefusedTimes(0);
            case "reporter" -> tracker.setReporterRefusedTimes(0);
            case "agent" -> tracker.setAgentRefusedTimes(0);
            case "maniac" -> tracker.setManiacRefusedTimes(0);
            case "hitman" -> tracker.setHitmanRefusedTimes(0);
            case "broad" -> tracker.setBroadRefusedTimes(0);
            case "hacker" -> tracker.setHackerRefusedTimes(0);
            case "bodyguard" -> tracker.setBodyguardRefusedTimes(0);
            case "necromancer" -> tracker.setNecroRefusedTimes(0);

        }

        roleRefusalTrackerRepository.save(tracker);
    }

    public void assignTownsfolkToRemainingPlayers(GameSessionRuntime game) {
        Role townsfolkRole = roleRepository.findByRoleName("Townsfolk")
                .orElseThrow(() -> new IllegalStateException("Townsfolk role not found!"));

        for (PlayerInGame player : game.getPlayers()) {
            if (player.getRole() == null) {
                player.setRole(townsfolkRole);
                player.setAlignment(townsfolkRole.getAlignment());

//                game.addPublicMessage(player.getUser().getUsername() +
//                        " has taken up the humble duties of a Townsfolk.");
                game.addLog("Assigned Townsfolk to " + player.getUser().getUsername());
            }
        }
    }

    @Override
    public void assignPrivateChatAccess(GameSessionRuntime game) {
        List<Long> officeMembers = new ArrayList<>();
        List<Long> hideoutMembers = new ArrayList<>();
        List<Long> graveyardMembers = new ArrayList<>();

        for (PlayerInGame player : game.getPlayers()) {
            String role = player.getRole().getRoleName().toLowerCase();

            switch (role) {
                case "sheriff", "bum":
                    officeMembers.add(player.getUser().getId());
                    break;
                case "mafia", "lawyer", "reporter", "agent":
                    hideoutMembers.add(player.getUser().getId());
                    break;
                case "necromancer":
                    graveyardMembers.add(player.getUser().getId());
                    break;
            }
        }

        // Store in stageData so frontend can read it
        game.getStageData().put("office_members", officeMembers);
        game.getStageData().put("hideout_members", hideoutMembers);
        game.getStageData().put("graveyard_members", graveyardMembers);

        game.addLog("Private channel access assigned.");
    }

    @Override
    public void assignTierThresholds(GameSessionRuntime game) {
        int playerCount = game.getPlayers().size();
        double scalingFactor = playerCount / 16.0;

        Map<String, Integer> thresholds = new HashMap<>();
        thresholds.put("tier2", (int)(60 * scalingFactor));
        thresholds.put("tier3", (int)(140 * scalingFactor));
        thresholds.put("tier4", (int)(240 * scalingFactor));
        thresholds.put("broad_hacker_win", (int)(320 * scalingFactor));

        game.getStageData().put("tierThresholds", thresholds);
        game.addLog("Tier thresholds assigned based on " + playerCount + " players.");
    }

    @Override
    public boolean allNightActionsComplete(GameSessionRuntime game) {
        for (PlayerInGame player : game.getPlayers()) {
            Role role = player.getRole();

            // Skip roles like Vampire, Demon, Ghost, etc. (not yet implemented)
            if (role == null
                    || role.getRoleName().equalsIgnoreCase("ghost")
                    || role.getRoleName().equalsIgnoreCase("townsfolk")
                    || role.getRoleName().equalsIgnoreCase("vampire")
                    || role.getRoleName().equalsIgnoreCase("demon")
                ) {
                continue;
            }

            // If role has not submitted an action, night is not done
            if (!player.isHasActedTonight()) {
                return false;
            }
        }
        return true;
    }

    /** Remove a finished/canceled game and scrub transient state to help GC. */
    public boolean removeGame(UUID sessionId) {
        GameSessionRuntime target = null;
        for (GameSessionRuntime g : activeGames) {
            if (g.getSessionId().equals(sessionId)) {
                target = g;
                break;
            }
        }

        if (target != null) {
            cleanupRuntime(target);
            activeGames.remove(target);

            // 🧹 Remove from the global registry as well
            if (target.getGame() != null && target.getGame().getId() != null) {
                gameRegistry.removeGame(target.getGame().getId());
            }

            System.out.println("🧹 Game " + sessionId + " removed from manager and registry.");
            return true;
        }

        return false;
    }

    /** Minimal best-effort cleanup; safe even if some keys/methods are absent. */
    @SuppressWarnings("unchecked")
    private void cleanupRuntime(GameSessionRuntime game) {
        // 1) Clear stageData (or at least known heavy keys)
        Map<String, Object> sd = game.getStageData();
        if (sd != null) {
            sd.keySet().removeAll(Set.of(
                    "phaseStartedAt",
                    "mafiaOrder",
                    "currentMafiaIndex",
                    "lynchTarget",
                    "lynchStartedAt",
                    "currentTally",
                    "contractOrders",
                    "winnerAlignment",
                    "winnerAnnouncement",
                    "isDraw",
                    "allDead",
                    "lastVoteTimestamps",
                    "nightActions" // if present
            ));
            sd.clear(); // full wipe, since game is over
        }

        // 2) Clear votes/verdicts (helpers you already added)
        try { game.clearVotes(); } catch (Exception ignored) {}
        try { game.clearVerdictVotes(); } catch (Exception ignored) {}

        // 3) Moved to common block

        // 4) Null or reset hanging/accused state
        try { game.setAccusedUserId(null); } catch (Exception ignored) {}

        // 5) Players list can be heavy; deref if you don’t need post-game inspection
        List<PlayerInGame> players = game.getPlayers();
        if (players != null) {
            players.clear();
        }

        // 6) Logs were archived already; free them
        List<String> log = game.getLog();
        if (log != null) log.clear();
    }

    public GameSessionRuntime getGameById(UUID sessionId) {
        return activeGames.stream()
                .filter(g -> g.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    public int clearAllGames() {
        int count = activeGames.size();
        activeGames.clear();
        return count;
    }

    public GameSessionRuntime findGameById(UUID sessionId) {
        return activeGames.stream()
                .filter(g -> g.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    private void removeFromMafiaOrderIfApplicable(GameSessionRuntime game, PlayerInGame victim) {
        if (!victim.getRole().getRoleName().equalsIgnoreCase("mafia")) return;

        Map<String, Object> stageData = game.getStageData();
        @SuppressWarnings("unchecked")
        List<Long> mafiaOrder = (List<Long>) stageData.get("mafiaOrder");
        Integer currentIndex = (Integer) stageData.get("currentMafiaIndex");

        if (mafiaOrder != null && !mafiaOrder.isEmpty()) {
            mafiaOrder.remove(victim.getUser().getId());
            game.addLog("Removed dead mafia " + victim.getUser().getUsername() + " from mafia order.");

            if (currentIndex != null && currentIndex >= mafiaOrder.size()) {
                stageData.put("currentMafiaIndex", 0);
            }

            stageData.put("mafiaOrder", mafiaOrder);
        }
    }

    public void handlePlayerDeath(GameSessionRuntime game, PlayerInGame victim, Long killerId) {
        Map<String, Object> stageData = game.getStageData();

        if (!victim.isAlive()) {
            game.addLog("⚠️ Tried to kill " + victim.getUser().getUsername() + " but they’re already dead.");
            return;
        }

        // 1️⃣ Mark as dead
        victim.setAlive(false);

        // 2️⃣ Identify killer (0 = crowd or system)
        String killerName = "the crowd";
        if (killerId != 0) {
            game.getPlayers().stream()
                    .filter(p -> p.getUser().getId().equals(killerId))
                    .findFirst()
                    .ifPresent(p -> game.addLog("Killer found: " + p.getUser().getUsername()));
            killerName = game.getPlayers().stream()
                    .filter(p -> p.getUser().getId().equals(killerId))
                    .map(p -> p.getUser().getUsername())
                    .findFirst()
                    .orElse("unknown forces");
        }

        game.addLog("Player " + victim.getUser().getUsername() + " was killed by " + killerName);

        // 3️⃣ Check ghost possibility
        boolean ghostCanAppear = false;
        Object ghostFlag = stageData.get("ghostCanAppear");
        if (ghostFlag instanceof Boolean flagValue) {
            ghostCanAppear = flagValue;
        }
        boolean ghostAlreadyPresent = game.getPlayers().stream()
                .anyMatch(p -> !p.isAlive() && "Ghost".equalsIgnoreCase(p.getRole().getRoleName()));

        boolean becameAGhost = false; // placeholder for later logic
        if (ghostCanAppear && !ghostAlreadyPresent && killerId != 0) {
            System.out.println("Ghost appearance mechanism stubbed here");
        }

        // 4️⃣ Death message logic
        boolean necromancerPresent = game.getPlayers().stream()
                .anyMatch(p -> p.isAlive() && "Necromancer".equalsIgnoreCase(p.getRole().getRoleName()));

        String deathNote = "";
        if (becameAGhost) {
            deathNote = "You see your dead body lying still somewhere below while you float up, observing Mafsville's daily rush from above. Seems like something is still holding you here in this world. You have become an ethereal Ghost. Now, the only purpose of your existence is to find your killer and have your revenge! Just wait for the night to come now...";
        } else if (necromancerPresent) {
            deathNote = "Unfortunately, you have been killed. This game seems to be over, but you can stay and wait for a little longer - who knows what that Necromancer might have in mind for you...";
        } else {
            deathNote = "Unfortunately, you have been killed. You can leave this game now, or stay around and see how it all ends.";
        }

        // 5️⃣ Notify victim privately
        privateMessagingService.sendPrivateMessage(
                victim.getUser().getId(),
                "☠️ " + deathNote
        );

        // 6️⃣ Clean up any rotations or stage effects
        removeFromMafiaOrderIfApplicable(game, victim);
    }

    public boolean leaveGame(GameSessionRuntime game, Long userId) {
        Iterator<PlayerInGame> iter = game.getPlayers().iterator();
        while (iter.hasNext()) {
            PlayerInGame p = iter.next();
            if (p.getUser().getId().equals(userId)) {
                iter.remove();
                game.addLog("Player " + p.getUser().getUsername() + " left the game.");
                return true;
            }
        }
        return false;
    }
}
