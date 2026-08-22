package com.mafia.mafia_backend.domain.dto;

public record LocationMemberDTO(
        Long userId,
        String username,
        String role,
        String membershipType
) {
}
