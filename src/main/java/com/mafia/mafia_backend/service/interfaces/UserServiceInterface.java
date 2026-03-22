package com.mafia.mafia_backend.service.interfaces;

import com.mafia.mafia_backend.domain.dto.UserRegistrationDTO;
import com.mafia.mafia_backend.domain.entity.RoleRefusalTracker;
import com.mafia.mafia_backend.domain.entity.User;

public interface UserServiceInterface {
    User registerUser(UserRegistrationDTO dto);
    User getUserById(Long id);
    void modifyMoney(Long userId, int delta);
    RoleRefusalTracker getOrCreateTracker(Long userId);
}