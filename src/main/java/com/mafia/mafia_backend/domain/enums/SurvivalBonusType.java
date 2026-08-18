package com.mafia.mafia_backend.domain.enums;

public enum SurvivalBonusType {
    NIGHT(1, 3, 10),
    MAFIA_DAY(2, 4, 12),
    NEUTRAL_DAY(1, 3, 10);

    private final int fourPlayerAmount;
    private final int sixteenPlayerAmount;
    private final int maxAmount;

    SurvivalBonusType(int fourPlayerAmount, int sixteenPlayerAmount, int maxAmount) {
        this.fourPlayerAmount = fourPlayerAmount;
        this.sixteenPlayerAmount = sixteenPlayerAmount;
        this.maxAmount = maxAmount;
    }

    public int getFourPlayerAmount() {
        return fourPlayerAmount;
    }

    public int getSixteenPlayerAmount() {
        return sixteenPlayerAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }
}
