package com.mafia.mafia_backend.domain.dto;

public record LocationMembershipResponse(
        String location,
        Long targetId,
        String membershipType,
        boolean changed,
        String message
) {
}
