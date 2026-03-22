package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractsController {

    private final GameManagerService gameManagerService;

    // ---- DTOs ----
    public record PlaceContractRequest(
            UUID sessionId,
            Long issuerId,
            Long targetId,
            int amount
    ) {}

    public record PlaceContractResponse(
            boolean success,
            String message
    ) {}

    @PostMapping("/place")
    public ResponseEntity<PlaceContractResponse> placeContract(@RequestBody PlaceContractRequest req) {
        if (req == null || req.sessionId() == null || req.issuerId() == null || req.targetId() == null) {
            return ResponseEntity.badRequest().body(new PlaceContractResponse(false, "Missing required fields."));
        }
        if (req.amount() <= 0) {
            return ResponseEntity.badRequest().body(new PlaceContractResponse(false, "Amount must be positive."));
        }

        // Locate game by sessionId among active games (adjust if you have a direct lookup)
        Optional<GameSessionRuntime> gameOpt = gameManagerService.getActiveGames().stream()
                .filter(g -> g.getSessionId().equals(req.sessionId()))
                .findFirst();

        if (gameOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new PlaceContractResponse(false, "Game session not found or inactive."));
        }

        GameSessionRuntime game = gameOpt.get();

        // Phase guard: contracts are only accepted during CONTRACTS
        if (game.getStage() != GamePhase.CONTRACTS) {
            return ResponseEntity.status(409).body(new PlaceContractResponse(false, "Contracts are not being accepted right now."));
        }

        // Require a living Hitman in this match, otherwise skip Contracts phase entirely
        boolean hitmanAlive = game.getPlayers().stream()
                .anyMatch(p -> p.isAlive() && "HITMAN".equalsIgnoreCase(p.getRole().getRoleName()));
        if (!hitmanAlive) {
            return ResponseEntity.status(409).body(new PlaceContractResponse(false, "No Hitman available. Contracts closed."));
        }

        // Basic issuer/target checks
        Optional<PlayerInGame> issuerOpt = game.findPlayerById(req.issuerId());
        Optional<PlayerInGame> targetOpt = game.findPlayerById(req.targetId());

        if (issuerOpt.isEmpty() || targetOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new PlaceContractResponse(false, "Issuer or target not found."));
        }
        PlayerInGame issuer = issuerOpt.get();
        PlayerInGame target = targetOpt.get();

        if (!issuer.isAlive()) {
            return ResponseEntity.status(409).body(new PlaceContractResponse(false, "Dead players can’t place contracts."));
        }
        if (!target.isAlive()) {
            return ResponseEntity.status(409).body(new PlaceContractResponse(false, "Target must be alive."));
        }

        // Funds check (contracts are limited by current in-game money)
        if (issuer.getInGameMoney() < req.amount()) {
            return ResponseEntity.status(409).body(new PlaceContractResponse(false, "Insufficient funds for this contract."));
        }

        // Delegate to runtime helper (recommended).
        // NOTE: Add game.placeContract(issuerId, targetId, amount) if you haven’t yet (as we outlined earlier).
        boolean placed = game.placeContract(req.issuerId(), req.targetId(), req.amount());
        if (!placed) {
            return ResponseEntity.status(409).body(new PlaceContractResponse(false, "Unable to place contract (validation failed)."));
        }

        return ResponseEntity.ok(new PlaceContractResponse(true, "Contract placed successfully."));
    }
}

