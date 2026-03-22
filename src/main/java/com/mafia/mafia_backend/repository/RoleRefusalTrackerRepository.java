package com.mafia.mafia_backend.repository;

import com.mafia.mafia_backend.domain.entity.RoleRefusalTracker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRefusalTrackerRepository extends JpaRepository<RoleRefusalTracker, Long> {
    Optional<RoleRefusalTracker> findByUserId(Long userId);
}
