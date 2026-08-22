package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.LocationAccessDTO;
import com.mafia.mafia_backend.domain.dto.LocationMemberDTO;
import com.mafia.mafia_backend.domain.dto.LocationMembersDTO;
import com.mafia.mafia_backend.domain.dto.LocationMembershipResponse;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.enums.MembershipType;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.model.PrivateLocationMembership;
import com.mafia.mafia_backend.domain.model.PrivateLocationState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PrivateLocationService {

    public void initializeNativeMemberships(GameSessionRuntime game) {
        PrivateLocationState state = game.getPrivateLocationState();
        if (state != null && state.isInitialized()) {
            syncLegacyStageData(game);
            return;
        }

        state = new PrivateLocationState();
        for (PlayerInGame player : game.getPlayers()) {
            Optional<PrivateLocation> nativeLocation = nativeLocationFor(player);
            if (nativeLocation.isPresent()) {
                state.putMembership(nativeLocation.get(), player.getUser().getId(), MembershipType.NATIVE, null);
            }
        }

        state.setInitialized(true);
        game.setPrivateLocationState(state);
        syncLegacyStageData(game);
        game.addLog("Private location memberships initialized.");
    }

    public boolean isMember(GameSessionRuntime game, Long userId, PrivateLocation location) {
        return getMembershipType(game, userId, location).isPresent();
    }

    public boolean isNativeMember(GameSessionRuntime game, Long userId, PrivateLocation location) {
        return getMembershipType(game, userId, location)
                .filter(type -> type == MembershipType.NATIVE)
                .isPresent();
    }

    public Optional<MembershipType> getMembershipType(GameSessionRuntime game, Long userId, PrivateLocation location) {
        return stateFor(game).getMembership(location, userId).map(PrivateLocationMembership::getType);
    }

    public List<PrivateLocationMembership> getMembers(GameSessionRuntime game, PrivateLocation location) {
        return stateFor(game).membershipsFor(location).values().stream()
                .sorted(Comparator.comparing(PrivateLocationMembership::getUserId))
                .map(membership -> new PrivateLocationMembership(
                        membership.getUserId(),
                        membership.getLocation(),
                        membership.getType(),
                        membership.getInvitedByUserId()))
                .toList();
    }

    public Optional<PrivateLocation> findNativeLoyaltyConflict(GameSessionRuntime game, Long actorId, Long targetId) {
        ensurePlayerExists(game, actorId);
        ensurePlayerExists(game, targetId);

        for (PrivateLocation location : PrivateLocation.values()) {
            if (isNativeMember(game, actorId, location) && isMember(game, targetId, location)) {
                return Optional.of(location);
            }
        }

        return Optional.empty();
    }

    public LocationAccessDTO getAccessibleLocations(GameSessionRuntime game, Long userId) {
        ensurePlayerExists(game, userId);

        List<String> locations = new ArrayList<>();
        for (PrivateLocation location : PrivateLocation.values()) {
            if (isMember(game, userId, location)) {
                locations.add(location.name());
            }
        }
        return new LocationAccessDTO(locations);
    }

    public LocationMembersDTO getVisibleMembers(GameSessionRuntime game, PrivateLocation location, Long requesterId) {
        if (!isMember(game, requesterId, location)) {
            throw new SecurityException("Requester is not a member of " + location + ".");
        }

        List<LocationMemberDTO> members = getMembers(game, location).stream()
                .map(membership -> {
                    PlayerInGame player = findPlayer(game, membership.getUserId());
                    return new LocationMemberDTO(
                            player.getUser().getId(),
                            player.getUser().getUsername(),
                            roleName(player),
                            membership.getType().name());
                })
                .toList();

        return new LocationMembersDTO(location.name(), members);
    }

    public LocationMembershipResponse invite(GameSessionRuntime game, PrivateLocation location, Long actorId, Long targetId) {
        requireManualLocation(location, "invite");
        requireActorMember(game, location, actorId);
        PlayerInGame target = requireLivingPlayer(game, targetId);

        Optional<MembershipType> existing = getMembershipType(game, target.getUser().getId(), location);
        if (existing.isPresent()) {
            return new LocationMembershipResponse(
                    location.name(),
                    target.getUser().getId(),
                    existing.get().name(),
                    false,
                    "Player is already a member of " + location + ".");
        }

        stateFor(game).putMembership(location, target.getUser().getId(), MembershipType.INVITED, actorId);
        syncLegacyStageData(game);
        game.addLog(target.getUser().getUsername() + " was invited to " + location + ".");
        return new LocationMembershipResponse(
                location.name(),
                target.getUser().getId(),
                MembershipType.INVITED.name(),
                true,
                "Player invited to " + location + ".");
    }

    public LocationMembershipResponse banish(GameSessionRuntime game, PrivateLocation location, Long actorId, Long targetId) {
        requireManualLocation(location, "banish");
        requireActorMember(game, location, actorId);
        ensurePlayerExists(game, targetId);

        PrivateLocationMembership membership = stateFor(game).getMembership(location, targetId)
                .orElseThrow(() -> new IllegalStateException("Target is not a member of " + location + "."));

        if (membership.getType() == MembershipType.NATIVE) {
            throw new IllegalStateException("Native members cannot be banished from " + location + ".");
        }

        stateFor(game).removeMembership(location, targetId);
        syncLegacyStageData(game);
        game.addLog("Invited member " + targetId + " was banished from " + location + ".");
        return new LocationMembershipResponse(
                location.name(),
                targetId,
                membership.getType().name(),
                true,
                "Player banished from " + location + ".");
    }

    public void removeFromLivingLocations(GameSessionRuntime game, Long userId) {
        PrivateLocationState state = stateFor(game);
        boolean changed = false;
        for (PrivateLocation location : List.of(PrivateLocation.OFFICE, PrivateLocation.HIDEOUT)) {
            if (state.getMembership(location, userId).isPresent()) {
                state.removeMembership(location, userId);
                changed = true;
            }
        }

        if (changed) {
            syncLegacyStageData(game);
            game.addLog("Removed dead player " + userId + " from living private locations.");
        }
    }

    public void syncLegacyStageData(GameSessionRuntime game) {
        PrivateLocationState state = stateFor(game);
        game.getStageData().put("office_members", memberIds(state, PrivateLocation.OFFICE));
        game.getStageData().put("hideout_members", memberIds(state, PrivateLocation.HIDEOUT));
        game.getStageData().put("graveyard_members", memberIds(state, PrivateLocation.GRAVEYARD));
    }

    private PrivateLocationState stateFor(GameSessionRuntime game) {
        if (game.getPrivateLocationState() == null) {
            game.setPrivateLocationState(new PrivateLocationState());
        }
        return game.getPrivateLocationState();
    }

    private Optional<PrivateLocation> nativeLocationFor(PlayerInGame player) {
        Role role = player.getRole();
        if (role == null || role.getRoleName() == null) {
            return Optional.empty();
        }

        String roleName = role.getRoleName().toLowerCase();
        return switch (roleName) {
            case "sheriff", "bum" -> Optional.of(PrivateLocation.OFFICE);
            case "mafia" -> Optional.of(PrivateLocation.HIDEOUT);
            default -> Optional.empty();
        };
    }

    private void requireManualLocation(PrivateLocation location, String operation) {
        if (location == PrivateLocation.GRAVEYARD) {
            throw new IllegalStateException("Manual " + operation + " is not allowed for GRAVEYARD.");
        }
    }

    private void requireActorMember(GameSessionRuntime game, PrivateLocation location, Long actorId) {
        ensurePlayerExists(game, actorId);
        if (!isMember(game, actorId, location)) {
            throw new SecurityException("Actor is not a member of " + location + ".");
        }
    }

    private PlayerInGame requireLivingPlayer(GameSessionRuntime game, Long userId) {
        PlayerInGame player = ensurePlayerExists(game, userId);
        if (!player.isAlive()) {
            throw new IllegalStateException("Target must be alive to join " + PrivateLocation.OFFICE + " or " + PrivateLocation.HIDEOUT + ".");
        }
        return player;
    }

    private PlayerInGame ensurePlayerExists(GameSessionRuntime game, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        return findPlayer(game, userId);
    }

    private PlayerInGame findPlayer(GameSessionRuntime game, Long userId) {
        return game.findPlayerById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Player is not part of this game."));
    }

    private String roleName(PlayerInGame player) {
        return player.getRole() == null ? null : player.getRole().getRoleName();
    }

    private List<Long> memberIds(PrivateLocationState state, PrivateLocation location) {
        Map<Long, PrivateLocationMembership> memberships = state.membershipsFor(location);
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }
        return memberships.keySet().stream().sorted().toList();
    }
}
