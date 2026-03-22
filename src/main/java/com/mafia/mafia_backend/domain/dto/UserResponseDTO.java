package com.mafia.mafia_backend.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class UserResponseDTO {
    private Long id;
    private String username;
    private String email;          // keep if you want it visible
    private LocalDateTime joinDate;
    private Long money;
    private String userStatus;     // or enum type if you prefer
    private boolean active;
    private String deviceInfo;
}

