package com.mafia.mafia_backend.service.interfaces;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.enums.SurvivalBonusType;

import java.util.Map;

public interface GameEconomyServiceInterface {
    int getTierForMoney(GameSessionRuntime game, int money);
    int getTierForMoney(GameSessionRuntime game, long money);
    int scaleRewardAmount(GameSessionRuntime game, int baseAmount);
    int scaleSurvivalBonusAmount(GameSessionRuntime game, SurvivalBonusType bonusType, int referenceAmount);
    void adjustMoney(GameSessionRuntime game, PlayerInGame player, long delta, String reason);
    Map<String, Integer> getTierThresholdsFromStageData(GameSessionRuntime game);
    int getMaxDigAmount(GameSessionRuntime game);
}
