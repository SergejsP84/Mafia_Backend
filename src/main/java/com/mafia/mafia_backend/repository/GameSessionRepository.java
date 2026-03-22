package com.mafia.mafia_backend.repository;

import com.mafia.mafia_backend.domain.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
}
