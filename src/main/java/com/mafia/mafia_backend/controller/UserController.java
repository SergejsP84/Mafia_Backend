package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.dto.UserRegistrationDTO;
import com.mafia.mafia_backend.domain.dto.UserResponseDTO;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.mappers.UserMapper;
import com.mafia.mafia_backend.repository.UserRepository;
import com.mafia.mafia_backend.service.implementation.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> registerUser(@Valid @RequestBody UserRegistrationDTO dto) {
        User newUser = userService.registerUser(dto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserMapper.toDto(newUser));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            Map<String, Object> result = userService.login(username, password);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
//    public ResponseEntity<?> getCurrentUser(@RequestParam String username) {
//        try {
//            Map<String, Object> result = userService.getUserInfo(username);
//            return ResponseEntity.ok(result);
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.badRequest()
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
    public ResponseEntity<?> me(Authentication auth) {
        return ResponseEntity.ok(Map.of(
                "user", auth != null ? auth.getName() : "none"
        ));
    }
}
