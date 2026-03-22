package com.mafia.mafia_backend.service.interfaces;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;

import java.util.Map;

public interface GameEconomyServiceInterface {
    int getTierForMoney(GameSessionRuntime game, int money);
    Map<String, Integer> getTierThresholdsFromStageData(GameSessionRuntime game);
    int getMaxDigAmount(GameSessionRuntime game);
}
