package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;
    private final GameManagerService gameManagerService;

    @GetMapping
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @PostMapping("/{gameId}/{userId}/confirm-role")
    public ResponseEntity<Map<String, String>> confirmRole(
            @PathVariable Long gameId,
            @PathVariable Long userId) {
        try {
            String confirmedRole = gameManagerService.confirmOfferedRole(gameId, userId);

            Map<String, String> response = new HashMap<>();
            response.put("role", confirmedRole.toLowerCase());

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{gameId}/{userId}/refuse-role")
    public ResponseEntity<String> refuseRole(
            @PathVariable Long gameId,
            @PathVariable Long userId) {
        try {
            gameManagerService.refuseOfferedRole(gameId, userId);
            return ResponseEntity.ok("Role refused!");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}