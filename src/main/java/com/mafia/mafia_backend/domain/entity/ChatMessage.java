package com.mafia.mafia_backend.domain.entity;

import com.mafia.mafia_backend.domain.enums.ChatChannelType;

import java.time.LocalDateTime;

public class ChatMessage {
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    private ChatChannelType channel; // PUBLIC, PRIVATE, MAFIA, etc.
}