package com.mafia.mafia_backend.domain.entity;

import com.mafia.mafia_backend.domain.enums.Alignment;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "players_in_game")
public class PlayerInGameEntity {

    public PlayerInGameEntity(User user, boolean isAlive) {
        this.user = user;
        this.isAlive = isAlive;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private GameSession gameSession;

    private boolean isAlive = true;

    @ManyToOne
    private Role currentRole;

    @Enumerated(EnumType.STRING)
    private Alignment currentAlignment; // Independent and mutable

    private int tierInGame = 1;

    private Integer moneyInGame = 0;

    private Long votedForPlayerId; // optional, tracks voting
}

