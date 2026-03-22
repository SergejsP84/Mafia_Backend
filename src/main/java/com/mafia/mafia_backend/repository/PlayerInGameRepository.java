package com.mafia.mafia_backend.repository;

import com.mafia.mafia_backend.domain.entity.PlayerInGameEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerInGameRepository extends JpaRepository<PlayerInGameEntity, Long> {

}
