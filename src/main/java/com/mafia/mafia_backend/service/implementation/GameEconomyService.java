package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.enums.SurvivalBonusType;
import com.mafia.mafia_backend.service.interfaces.GameEconomyServiceInterface;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GameEconomyService implements GameEconomyServiceInterface {

    @Override
    public int getTierForMoney(GameSessionRuntime game, int money) {
        return getTierForMoney(game, (long) money);
    }

    @Override
    public int getTierForMoney(GameSessionRuntime game, long money) {
        Map<String, Integer> t = getTierThresholdsFromStageData(game);
        if (money >= t.get("tier4")) return 4;
        if (money >= t.get("tier3")) return 3;
        if (money >= t.get("tier2")) return 2;
        return 1;
    }

    @Override
    public int scaleRewardAmount(GameSessionRuntime game, int baseAmount) {
        if (baseAmount == 0) {
            return 0;
        }

        int playerCount = getInitialPlayerCount(game);
        double rewardScale = Math.sqrt(playerCount / 16.0);
        int scaledAmount = (int) Math.round(baseAmount * rewardScale);

        if (scaledAmount == 0) {
            return baseAmount > 0 ? 1 : -1;
        }
        return scaledAmount;
    }

    @Override
    public int scaleSurvivalBonusAmount(GameSessionRuntime game, SurvivalBonusType bonusType, int referenceAmount) {
        if (referenceAmount <= 0) {
            return 0;
        }

        int playerCount = getInitialPlayerCount(game);
        int anchoredAmount;

        if (playerCount <= 4) {
            anchoredAmount = bonusType.getFourPlayerAmount();
        } else if (playerCount >= 50) {
            anchoredAmount = bonusType.getMaxAmount();
        } else if (playerCount <= 16) {
            anchoredAmount = interpolate(
                    playerCount,
                    4,
                    bonusType.getFourPlayerAmount(),
                    16,
                    bonusType.getSixteenPlayerAmount()
            );
        } else {
            anchoredAmount = interpolate(
                    playerCount,
                    16,
                    bonusType.getSixteenPlayerAmount(),
                    50,
                    bonusType.getMaxAmount()
            );
        }

        double configuredMultiplier = referenceAmount / (double) bonusType.getSixteenPlayerAmount();
        int scaledAmount = (int) Math.round(anchoredAmount * configuredMultiplier);
        return Math.min(bonusType.getMaxAmount(), Math.max(1, scaledAmount));
    }

    @Override
    public void adjustMoney(GameSessionRuntime game, PlayerInGame player, long delta, String reason) {
        if (player == null) {
            if (game != null) {
                game.addLog("WARNING Money adjustment skipped for null player. Reason: " + reason);
            }
            return;
        }

        long beforeMoney = player.getInGameMoney();
        int beforeTier = player.getTier();
        long afterMoney = beforeMoney + delta;
        int afterTier = getTierForMoney(game, afterMoney);

        player.setInGameMoney(afterMoney);
        player.setTier(afterTier);

        if (game != null) {
            String username = player.getUser() != null ? player.getUser().getUsername() : "unknown";
            game.addLog("Money changed for " + username + " by " + signed(delta)
                    + " (" + reason + "): " + beforeMoney + " -> " + afterMoney);

            if (beforeTier != afterTier) {
                String direction = afterTier > beforeTier ? "advanced" : "dropped";
                game.addLog("Tier changed for " + username + ": " + beforeTier
                        + " -> " + afterTier + " (" + direction + ")");
            }
        }
    }

    private String signed(long value) {
        return value >= 0 ? "+" + value : Long.toString(value);
    }

    private int getInitialPlayerCount(GameSessionRuntime game) {
        if (game == null || game.getInitialPlayerCount() == null || game.getInitialPlayerCount() <= 0) {
            throw new IllegalStateException("Initial player count is required for reward scaling.");
        }
        return game.getInitialPlayerCount();
    }

    private int interpolate(int playerCount, int lowPlayers, int lowAmount, int highPlayers, int highAmount) {
        double raw = lowAmount + ((playerCount - lowPlayers) * (highAmount - lowAmount) / (double) (highPlayers - lowPlayers));
        return (int) Math.round(raw);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getTierThresholdsFromStageData(GameSessionRuntime game) {
        Object rawThresholds = game.getStageData().get("tierThresholds");
        if (rawThresholds instanceof Map<?, ?> map) {
            try {
                // Safe cast assuming keys are String and values are Integer
                return (Map<String, Integer>) map;
            } catch (ClassCastException e) {
                game.addLog("WARNING Tier threshold map has incorrect format:" + e);
            }
        }
        // Fallback values if not present or invalid
        return Map.of(
                "tier1", 0,
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        );
    }


    @Override
    public int getMaxDigAmount(GameSessionRuntime game) {
        Map<String, Integer> tierThresholds = getTierThresholdsFromStageData(game);
        int tier2Threshold = tierThresholds.getOrDefault("tier2", 60);
        return (int) Math.floor(tier2Threshold * 0.75);
    }



}
