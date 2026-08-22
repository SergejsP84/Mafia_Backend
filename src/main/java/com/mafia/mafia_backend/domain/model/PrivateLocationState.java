package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.enums.MembershipType;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
public class PrivateLocationState {
    private boolean initialized;
    private Map<PrivateLocation, Map<Long, PrivateLocationMembership>> memberships = new EnumMap<>(PrivateLocation.class);

    public PrivateLocationState() {
        for (PrivateLocation location : PrivateLocation.values()) {
            memberships.put(location, new LinkedHashMap<>());
        }
    }

    public Optional<PrivateLocationMembership> getMembership(PrivateLocation location, Long userId) {
        return Optional.ofNullable(membershipsFor(location).get(userId));
    }

    public void putMembership(PrivateLocation location, Long userId, MembershipType type, Long invitedByUserId) {
        membershipsFor(location).put(userId, new PrivateLocationMembership(userId, location, type, invitedByUserId));
    }

    public void removeMembership(PrivateLocation location, Long userId) {
        membershipsFor(location).remove(userId);
    }

    public Map<Long, PrivateLocationMembership> membershipsFor(PrivateLocation location) {
        return memberships.computeIfAbsent(location, key -> new LinkedHashMap<>());
    }
}
