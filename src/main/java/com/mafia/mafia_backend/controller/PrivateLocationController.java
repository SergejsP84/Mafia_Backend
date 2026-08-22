package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.dto.LocationAccessDTO;
import com.mafia.mafia_backend.domain.dto.LocationMembersDTO;
import com.mafia.mafia_backend.domain.dto.LocationMembershipChangeRequest;
import com.mafia.mafia_backend.domain.dto.LocationMembershipResponse;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import com.mafia.mafia_backend.service.implementation.PrivateLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class PrivateLocationController {

    private final GameManagerService gameManagerService;
    private final PrivateLocationService privateLocationService;

    @GetMapping("/{gameId}/{userId}")
    public ResponseEntity<?> access(@PathVariable Long gameId, @PathVariable Long userId) {
        GameSessionRuntime game = gameManagerService.findByGameId(gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Game not found"));
        }

        try {
            LocationAccessDTO response = privateLocationService.getAccessibleLocations(game, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/{location}/members")
    public ResponseEntity<?> members(
            @PathVariable Long gameId,
            @PathVariable String location,
            @RequestParam Long requesterId
    ) {
        GameSessionRuntime game = gameManagerService.findByGameId(gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Game not found"));
        }

        try {
            LocationMembersDTO response = privateLocationService.getVisibleMembers(
                    game,
                    parseLocation(location),
                    requesterId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/{location}/invite")
    public ResponseEntity<?> invite(
            @PathVariable Long gameId,
            @PathVariable String location,
            @RequestBody LocationMembershipChangeRequest request
    ) {
        return changeMembership(gameId, location, request, true);
    }

    @PostMapping("/{gameId}/{location}/banish")
    public ResponseEntity<?> banish(
            @PathVariable Long gameId,
            @PathVariable String location,
            @RequestBody LocationMembershipChangeRequest request
    ) {
        return changeMembership(gameId, location, request, false);
    }

    private ResponseEntity<?> changeMembership(
            Long gameId,
            String location,
            LocationMembershipChangeRequest request,
            boolean invite
    ) {
        if (request == null || request.actorId() == null || request.targetId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "actorId and targetId are required."));
        }

        GameSessionRuntime game = gameManagerService.findByGameId(gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Game not found"));
        }

        try {
            PrivateLocation parsedLocation = parseLocation(location);
            LocationMembershipResponse response = invite
                    ? privateLocationService.invite(game, parsedLocation, request.actorId(), request.targetId())
                    : privateLocationService.banish(game, parsedLocation, request.actorId(), request.targetId());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    private PrivateLocation parseLocation(String location) {
        try {
            return PrivateLocation.valueOf(location.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown private location: " + location + ".");
        }
    }
}
