package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.DigResponse;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DigService {

    private static final int PERMANENT_COST_MULTIPLIER = 30;

    private final UserRepository userRepository;
    private final GameEconomyService gameEconomyService;

    @Transactional
    public synchronized DigResponse dig(GameSessionRuntime game, Long userId, int amount) {
        validateGameState(game);

        PlayerInGame player = game.findPlayerByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found in game."));

        validatePlayer(player);
        validateAmountAgainstCap(game, amount);

        long debit = (long) amount * PERMANENT_COST_MULTIPLIER;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        long permanentBalance = safeMoney(user);
        if (permanentBalance < debit) {
            throw new IllegalArgumentException("Permanent account cannot cover this Dig.");
        }

        long newPermanentBalance = permanentBalance - debit;
        long beforeInGameMoney = player.getInGameMoney();
        int beforeTier = player.getTier();
        boolean beforeDug = player.isHasDugThisGame();
        long beforeRuntimePermanentBalance = player.getUser() != null ? safeMoney(player.getUser()) : permanentBalance;

        user.setMoney(newPermanentBalance);
        userRepository.saveAndFlush(user);

        try {
            gameEconomyService.adjustMoney(game, player, amount, "Digging");
            player.setHasDugThisGame(true);
            player.getUser().setMoney(newPermanentBalance);
        } catch (RuntimeException e) {
            player.setInGameMoney(beforeInGameMoney);
            player.setTier(beforeTier);
            player.setHasDugThisGame(beforeDug);
            if (player.getUser() != null) {
                player.getUser().setMoney(beforeRuntimePermanentBalance);
            }
            throw e;
        }

        String roleName = player.getRole() != null ? player.getRole().getRoleName() : "Unknown role";
        game.addPublicMessage(roleName + " was able to dig up $" + amount + ".");

        return new DigResponse(
                amount,
                debit,
                newPermanentBalance,
                player.getInGameMoney(),
                player.getTier(),
                player.isHasDugThisGame(),
                true
        );
    }

    public int getMaxDigAmount(GameSessionRuntime game, PlayerInGame player) {
        int digCap = gameEconomyService.getMaxDigAmount(game);
        if (player == null || player.getUser() == null) {
            return 0;
        }
        long permanentBalance = safeMoney(player.getUser());
        long affordable = permanentBalance / PERMANENT_COST_MULTIPLIER;
        return (int) Math.min(digCap, affordable);
    }

    private void validateGameState(GameSessionRuntime game) {
        if (game == null) {
            throw new IllegalArgumentException("Game not found.");
        }
        if (game.getStage() != GamePhase.NIGHT) {
            throw new IllegalStateException("Digging is only available during NIGHT.");
        }
        if (game.isFinished() || game.isAborted()) {
            throw new IllegalStateException("Digging is not available in this game state.");
        }
        if (game.isAdvancingPhase()
                || game.getStageData().containsKey("nightResolved")
                || Boolean.TRUE.equals(game.getStageData().get("nightResolutionInProgress"))) {
            throw new IllegalStateException("Night is already locked for resolution.");
        }
    }

    private void validatePlayer(PlayerInGame player) {
        if (!player.isAlive() && !isClassicalUndead(player)) {
            throw new IllegalStateException("Player cannot Dig in this state.");
        }
        if (isGhost(player)) {
            throw new IllegalStateException("Ghost cannot Dig.");
        }
        if (player.isHasDugThisGame()) {
            throw new IllegalStateException("Player has already Dug this game.");
        }
    }

    private void validateAmountAgainstCap(GameSessionRuntime game, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Dig amount must be positive.");
        }

        int digCap = gameEconomyService.getMaxDigAmount(game);
        if (amount > digCap) {
            throw new IllegalArgumentException("Dig amount exceeds the game Dig cap.");
        }
    }

    private boolean isGhost(PlayerInGame player) {
        return player.getRole() != null
                && player.getRole().getRoleName() != null
                && player.getRole().getRoleName().equalsIgnoreCase("ghost");
    }

    private boolean isClassicalUndead(PlayerInGame player) {
        if (player.getRole() == null || player.getRole().getRoleName() == null) {
            return false;
        }

        String roleName = player.getRole().getRoleName();
        return roleName.equalsIgnoreCase("vampire") || roleName.equalsIgnoreCase("demon");
    }

    private long safeMoney(User user) {
        return user.getMoney() == null ? 0L : user.getMoney();
    }
}
