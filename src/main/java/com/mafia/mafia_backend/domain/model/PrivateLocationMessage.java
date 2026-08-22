package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.enums.PrivateLocationMessageType;

import java.time.LocalDateTime;

public record PrivateLocationMessage(
        PrivateLocation location,
        PrivateLocationMessageType type,
        Long senderId,
        String senderName,
        String text,
        LocalDateTime timestamp
) {
}
