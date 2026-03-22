package com.mafia.mafia_backend.domain.dto;

import com.mafia.mafia_backend.domain.enums.GamePhase;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class GameStateDTO {
    private UUID sessionId;
    private GamePhase stage;
    private boolean finished;
    private List<PlayerInfo> players;
    private List<String> publicMessages;

    @AllArgsConstructor
    @Data
    public static class PlayerInfo {
        private String username;
        private String role;
        private boolean alive;
        private int money;
    }
}