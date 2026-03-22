package com.mafia.mafia_backend.domain.model;


import com.mafia.mafia_backend.domain.enums.NightActionType;

import java.time.LocalDateTime;

public class NightAction {
    private final Long actorId;
    private final Long targetId;
    private final NightActionType actionType;
    private final int nightNumber;
    private final LocalDateTime declaredAt;

    private boolean cancelled = false;

    public NightAction(Long actorId, Long targetId, NightActionType actionType, int nightNumber) {
        this.actorId = actorId;
        this.targetId = targetId;
        this.actionType = actionType;
        this.nightNumber = nightNumber;
        this.declaredAt = LocalDateTime.now();
    }

    public Long getActorId() {
        return actorId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public NightActionType getActionType() {
        return actionType;
    }

    public int getNightNumber() {
        return nightNumber;
    }

    public LocalDateTime getDeclaredAt() {
        return declaredAt;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}

