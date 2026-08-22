package com.mafia.mafia_backend.domain.dto;

import java.util.List;

public record LocationMembersDTO(String location, List<LocationMemberDTO> members) {
}
