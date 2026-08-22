package com.mafia.mafia_backend.domain.dto;

import java.time.LocalDateTime;

public record PrivateLocationMessageDTO(
        String location,
        String type,
        Long senderId,
        String senderName,
        String text,
        LocalDateTime timestamp
) {
}
