package com.mafia.mafia_backend.mappers;

import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.dto.UserResponseDTO;

public class UserMapper {
    public static UserResponseDTO toDto(User u) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setJoinDate(u.getJoinDate());
        dto.setMoney(u.getMoney());
        dto.setUserStatus(u.getUserStatus().name());
        dto.setActive(u.isActive());
        dto.setDeviceInfo(u.getDeviceInfo());
        return dto;
    }
}
