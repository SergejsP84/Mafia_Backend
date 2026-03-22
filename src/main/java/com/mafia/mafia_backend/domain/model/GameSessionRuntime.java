package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.service.implementation.ConfigSettingService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class GameSessionRuntime {

    private final ConfigSettingService configSettingService;

    private UUID sessionId = UUID.randomUUID();            // Unique session ID
    private LocalDateTime createdAt = LocalDateTime.now(); // When it was created
    private Game game;
    private GamePhase stage = GamePhase.LOBBY;             // Current stage
    private LocalDateTime stageStartedAt = LocalDateTime.now(); // When current stage began

    private List<PlayerInGame> players = new ArrayList<>();     // All players in this match
    private Map<String, Object> stageData = new HashMap<>();    // Optional per-stage info
    private List<String> log = new ArrayList<>();               // Game log for summary

    private boolean isAborted = false;                     // In case not enough players joined
    private boolean isFinished = false;                    // Victory condition met
    // Maps night number -> list of actions for that night
    private Map<Integer, List<NightAction>> nightActions = new HashMap<>();
    private Map<Long, VoteRecord> dayVotes = new HashMap<>(); // voterId -> their current vote
    // accused during this hanging cycle
    private Long accusedUserId;
    // verdict votes (confirmation phase): voterId -> vote
    private Map<Long, VerdictVoteRecord> verdictVotes = new HashMap<>();
    private final Map<Long, LocalDateTime> lastVoteTimestamps = new ConcurrentHashMap<>();
    private boolean advancingPhase = false;
    private final List<String> publicMessages = new ArrayList<>();

    private int currentNightNumber = 0;

    // Convenience methods
    public void incrementNightNumber() {
        this.currentNightNumber++;
    }

    public void resetNightNumber() {
        this.currentNightNumber = 0;
    }

    public GameSessionRuntime(ConfigSettingService configSettingService) {
        this.configSettingService = configSettingService;
    }

    public Optional<PlayerInGame> findPlayerByUsername(String username) {
        return players.stream()
                .filter(p -> p.getUser().getUsername().equals(username))
                .findFirst();
    }

    public Optional<PlayerInGame> findPlayerByUserId(Long userId) {
        return findPlayerById(userId);
    }

    public void advanceStage(GamePhase newStage) {
        this.stage = newStage;
        this.stageStartedAt = LocalDateTime.now();
    }

    public void addLog(String entry) {
        log.add(LocalDateTime.now() + " - " + entry);
        System.out.println("\uD83D\uDC37" + " " + LocalDateTime.now() + " - " + entry);
    }

    public void addPublicMessage(String message) {
        String formatted = LocalDateTime.now() + " [MafiaBOT]: " + message;

        // Store for logs
        log.add(formatted);

        // Store for public chat
        publicMessages.add(formatted);

        // Optional: limit to last N messages (say 500) to avoid memory overflow
        if (publicMessages.size() > 500) {
            publicMessages.remove(0);
        }
    }

    public List<String> getPublicMessages() {
        return publicMessages;
    }

    public Optional<PlayerInGame> findPlayerById(Long userId) {
        return players.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst();
    }

    public List<NightAction> getActionsForNight(int nightNumber) {
        return nightActions.computeIfAbsent(nightNumber, k -> new ArrayList<>());
    }

    public void addNightAction(int nightNumber, NightAction action) {
        getActionsForNight(nightNumber).removeIf(a -> a.getActorId().equals(action.getActorId()));
        getActionsForNight(nightNumber).add(action);
    }

    public void cancelNightAction(int nightNumber, Long actorId) {
        getActionsForNight(nightNumber).removeIf(a -> a.getActorId().equals(actorId));
    }

    public synchronized void castVote(Long voterId, Long targetId, boolean voteForNight) {
        GamePhase currentPhase = this.getStage();

        // 🕒 Check rate limit (configurable)
        int cooldownMs = configSettingService.getIntSetting("VoteCooldownMillis", 1000);
        boolean limiterEnabled = configSettingService.getBooleanSetting("VoteLimiterEnabled", true);
        if (!canVote(voterId, cooldownMs, limiterEnabled)) {
            addLog("Vote from user " + voterId + " rejected: rate limit exceeded.");
            return;
        }

        // --- Handle lynching rules ---
        if (currentPhase == GamePhase.LYNCHING) {
            String lynchTargetName = (String) stageData.get("lynchTarget");
            Optional<PlayerInGame> lynchTargetOpt = findPlayerByUsername(lynchTargetName);

            if (lynchTargetOpt.isEmpty()) {
                addLog("⚠️ Lynch target missing during LYNCHING phase — ignoring vote.");
                return;
            }

            PlayerInGame lynchTarget = lynchTargetOpt.get();
            boolean sameTarget = Objects.equals(targetId, lynchTarget.getUser().getId());

            // Reject any invalid vote
            if (!sameTarget || voteForNight) {
                addLog("Vote from user " + voterId + " rejected (invalid during LYNCHING).");
                return;
            }
        }

        // --- Record vote ---
        VoteRecord record = new VoteRecord(voterId, targetId, voteForNight, LocalDateTime.now());
        dayVotes.put(voterId, record);

        Map<String, Long> tally = (getStage() == GamePhase.LYNCHING)
                ? computeLynchTally()
                : computeVoteTally();

        addPublicMessage("📥 Vote updated! Current votes: " + tally);
    }

    public void clearVotes() {
        dayVotes.clear();
    }

    public Map<Long, VoteRecord> getAllVotes() {
        return dayVotes;
    }

    public Map<String, Long> computeVoteTally() {
        Map<String, Long> tally = new LinkedHashMap<>();

        long nightVotes = dayVotes.values().stream()
                .filter(VoteRecord::isVoteForNight)
                .count();
        if (nightVotes > 0) tally.put("NIGHT", nightVotes);

        players.stream()
                .filter(PlayerInGame::isAlive)
                .forEach(p -> {
                    long votes = dayVotes.values().stream()
                            .filter(v -> !v.isVoteForNight() && v.getTargetId() != null && v.getTargetId().equals(p.getUser().getId()))
                            .count();
                    if (votes > 0) tally.put(p.getUser().getUsername(), votes);
                });

        return tally;
    }

    public void announceVote(PlayerInGame voter, String targetName, Map<String, Long> tally) {
        String summary = tally.entrySet().stream()
                .map(e -> e.getKey() + " - " + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("No votes yet.");

        addPublicMessage("Player " + voter.getUser().getUsername()
                + " voted for " + (targetName != null ? ("hanging player " + targetName) : "the night to come") + "!");
        addPublicMessage("Current votes: " + summary);
    }

    public void clearVerdictVotes() { verdictVotes.clear(); }
    public Map<Long, VerdictVoteRecord> getVerdictVotes() { return verdictVotes; }

    public void castVerdictVote(Long voterId, VerdictChoice choice) {
        verdictVotes.put(voterId, new VerdictVoteRecord(voterId, choice, LocalDateTime.now()));
    }

    public Optional<PlayerInGame> getAccused() {
        return accusedUserId == null ? Optional.empty() : findPlayerById(accusedUserId);
    }
    public void setAccusedUserId(Long id) { this.accusedUserId = id; }
    public void clearAccused() { this.accusedUserId = null; }

    public Optional<PlayerInGame> getTopVotedCandidate(int playerCount) {
        Map<String, Long> tally = computeVoteTally();

        if (tally.isEmpty()) return Optional.empty();
        if (tally.size() < Math.round((float) playerCount / 2)) return Optional.empty(); // quorum not reached
        // Filter out "NIGHT" votes
        tally.remove("NIGHT");

        // Find top entry by votes
        Map.Entry<String, Long> top = tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (top == null) return Optional.empty();

        // Check for tie: any other candidate has the same number of votes
        long topVotes = top.getValue();
        boolean tie = tally.entrySet().stream()
                .filter(e -> !e.getKey().equals(top.getKey()))
                .anyMatch(e -> e.getValue().equals(topVotes));

        if (tie) return Optional.empty(); // tie => no clear accused

        // Find player object by name
        return findPlayerByUsername(top.getKey());
    }

    public Map<String, Long> computeLynchTally() {
        Map<String, Long> tally = new LinkedHashMap<>();
        String lynchTarget = (String) stageData.get("lynchTarget");
        if (lynchTarget == null) return tally;

        long votesForTarget = dayVotes.values().stream()
                .filter(v -> !v.isVoteForNight())
                .filter(v -> {
                    PlayerInGame target = findPlayerByUsername(lynchTarget).orElse(null);
                    return target != null && v.getTargetId().equals(target.getUser().getId());
                })
                .count();

        if (votesForTarget > 0) tally.put(lynchTarget, votesForTarget);
        return tally;
    }

    public boolean canVote(Long voterId, int cooldownMillis, boolean limiterEnabled) {
        if (!limiterEnabled) return true;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastVote = lastVoteTimestamps.get(voterId);

        if (lastVote != null && Duration.between(lastVote, now).toMillis() < cooldownMillis) {
            return false; // within cooldown window
        }
        lastVoteTimestamps.put(voterId, now);
        return true;
    }

    public void cleanupOldVoteTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        lastVoteTimestamps.entrySet().removeIf(entry -> {
            // remove timestamps older than 30 minutes or belonging to dead players
            boolean isStale = Duration.between(entry.getValue(), now).toMinutes() > 30;
            boolean playerGone = players.stream()
                    .noneMatch(p -> p.getUser().getId().equals(entry.getKey()) && p.isAlive());
            return isStale || playerGone;
        });
    }

    public synchronized boolean beginPhaseAdvance() {
        if (advancingPhase) return false;   // another thread is already advancing
        advancingPhase = true;
        return true;
    }

    public synchronized void endPhaseAdvance() {
        advancingPhase = false;
    }

    @SuppressWarnings("unchecked")
    public boolean placeContract(Long issuerId, Long targetId, int amount) {
        if (issuerId == null || targetId == null || amount <= 0) return false;

        Optional<PlayerInGame> issuerOpt = findPlayerById(issuerId);
        Optional<PlayerInGame> targetOpt = findPlayerById(targetId);
        if (issuerOpt.isEmpty() || targetOpt.isEmpty()) return false;

        PlayerInGame issuer = issuerOpt.get();
        PlayerInGame target = targetOpt.get();

        // Must be alive to issue contracts
        if (!issuer.isAlive()) {
            addLog("❌ Contract rejected: " + issuer.getUser().getUsername() + " is dead.");
            return false;
        }

        // Target must be alive too
        if (!target.isAlive()) {
            addLog("❌ Contract rejected: target " + target.getUser().getUsername() + " is already dead.");
            return false;
        }

        // Can't offer more than you currently have (prevention of absurd bounties)
        if (issuer.getInGameMoney() < amount) {
            addLog("❌ Contract rejected: " + issuer.getUser().getUsername()
                    + " attempted to place a bounty exceeding their funds (" + amount + "$).");
            return false;
        }

        // Record order (no money deducted here)
        Map<String, Object> stageData = getStageData();
        List<ContractOrder> orders =
                (List<ContractOrder>) stageData.computeIfAbsent("contractOrders", k -> new ArrayList<>());

        orders.add(new ContractOrder(issuerId, targetId, amount, LocalDateTime.now()));

        addPublicMessage("🧾 " + issuer.getUser().getUsername()
                + " discreetly placed a contract with the underworld...");
        addLog("ContractOrder placed: issuer=" + issuer.getUser().getUsername()
                + ", target=" + target.getUser().getUsername()
                + ", amount=$" + amount);

        return true;
    }

    // 🧨 NOTE: Used by Hitman role logic to view combined contract bounties.
//          Implemented early for future integration (see Hitman phase handler).
    @SuppressWarnings("unchecked")
    public Map<Long, Integer> getAggregatedContracts() {
        Map<String, Object> stageData = getStageData();
        List<ContractOrder> orders = (List<ContractOrder>) stageData.get("contractOrders");

        Map<Long, Integer> aggregated = new HashMap<>();
        if (orders == null || orders.isEmpty()) return aggregated;

        for (ContractOrder order : orders) {
            aggregated.merge(order.getTargetId(), order.getAmount(), Integer::sum);
        }

        return aggregated;
    }

    public String summarizeContracts() {
        Map<Long, Integer> contracts = getAggregatedContracts();
        if (contracts.isEmpty()) return "No contracts placed this round.";

        StringBuilder sb = new StringBuilder("Current active contracts: ");
        for (Map.Entry<Long, Integer> entry : contracts.entrySet()) {
            String targetName = findPlayerById(entry.getKey())
                    .map(p -> p.getUser().getUsername())
                    .orElse("Unknown");
            sb.append(targetName).append(" ($").append(entry.getValue()).append("), ");
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}