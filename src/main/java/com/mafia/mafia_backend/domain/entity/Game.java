package com.mafia.mafia_backend.domain.entity;

import com.mafia.mafia_backend.domain.enums.GamePhase;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Data
@Getter
@Setter
@AllArgsConstructor
public class Game {
    private static final AtomicLong idCounter = new AtomicLong();

    private final Long id;
    private GamePhase phase;
    private LocalDateTime createdAt;

    public Game() {
        this.id = idCounter.incrementAndGet();
        this.phase = GamePhase.LOBBY;
        this.createdAt = LocalDateTime.now();
    }

    private final List<PlayerInGameEntity> players = new ArrayList<>();
}

