package com.mafia.mafia_backend.domain.model;


import com.mafia.mafia_backend.domain.enums.NightActionType;

import java.time.LocalDateTime;

public class NightAction {
    public static final int MAX_COMMENT_LENGTH = 512;

    private final Long actorId;
    private final Long targetId;
    private final NightActionType actionType;
    private final int nightNumber;
    private final LocalDateTime declaredAt;
    private final String comment;

    private boolean cancelled = false;

    public NightAction(Long actorId, Long targetId, NightActionType actionType, int nightNumber) {
        this(actorId, targetId, actionType, nightNumber, null);
    }

    public NightAction(Long actorId, Long targetId, NightActionType actionType, int nightNumber, String comment) {
        this.actorId = actorId;
        this.targetId = targetId;
        this.actionType = actionType;
        this.nightNumber = nightNumber;
        this.declaredAt = LocalDateTime.now();
        this.comment = normalizeComment(comment);
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

    public String getComment() {
        return comment;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    private String normalizeComment(String rawComment) {
        if (rawComment == null) {
            return null;
        }

        String trimmed = rawComment.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Action comment cannot exceed " + MAX_COMMENT_LENGTH + " characters.");
        }
        return trimmed;
    }
}

