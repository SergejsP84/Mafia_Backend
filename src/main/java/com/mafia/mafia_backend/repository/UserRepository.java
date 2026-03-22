package com.mafia.mafia_backend.repository;

import com.mafia.mafia_backend.domain.entity.RoleRefusalTracker;
import com.mafia.mafia_backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByDeviceInfo(String device_info);
    Optional<User> findByUsername(String username);
}
