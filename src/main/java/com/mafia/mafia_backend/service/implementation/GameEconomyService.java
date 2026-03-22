package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.service.interfaces.GameEconomyServiceInterface;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GameEconomyService implements GameEconomyServiceInterface {

    @Override
    public int getTierForMoney(GameSessionRuntime game, int money) {
        Map<String, Integer> t = (Map<String, Integer>) game.getStageData().get("tierThresholds");
        if (money >= t.get("tier4")) return 4;
        if (money >= t.get("tier3")) return 3;
        if (money >= t.get("tier2")) return 2;
        return 1;
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
        return (int) Math.floor(tier2Threshold * 2.0 / 3); // e.g. 60 * 2/3 = 40
    }



}
