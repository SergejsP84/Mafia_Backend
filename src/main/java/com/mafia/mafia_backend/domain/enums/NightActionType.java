package com.mafia.mafia_backend.domain.enums;

public enum NightActionType {
    SKIP(ActionDisposition.NONE),
    KILL(ActionDisposition.DETRIMENTAL),
    CHECK(ActionDisposition.NEUTRAL);
    // Future additions: HEAL, BLOCK, BITE, REVIVE, PROTECT, etc.

    private final ActionDisposition disposition;

    NightActionType(ActionDisposition disposition) {
        this.disposition = disposition;
    }

    public ActionDisposition getDisposition() {
        return disposition;
    }
}
