package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.enums.MembershipType;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PrivateLocationMembership {
    private Long userId;
    private PrivateLocation location;
    private MembershipType type;
    private Long invitedByUserId;
}
