package com.mafia.mafia_backend.domain.entity;

import com.mafia.mafia_backend.domain.enums.GamePhase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private boolean isStarted = false;
    private boolean isFinished = false;
    private boolean isCanceled = false;

    @Enumerated(EnumType.STRING)
    private GamePhase currentPhase;

    private LocalDateTime currentPhaseStart;
    private LocalDateTime currentPhaseEnd;

    private int minPlayers = 4;

    @OneToMany(mappedBy = "gameSession", cascade = CascadeType.ALL)
    private List<PlayerInGameEntity> players;

    // Optional future: who created the game
    // @ManyToOne
    // private User creator;
}
