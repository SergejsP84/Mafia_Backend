package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.UserRegistrationDTO;
import com.mafia.mafia_backend.domain.entity.RoleRefusalTracker;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.UserStatus;
import com.mafia.mafia_backend.repository.ConfigSettingRepository;
import com.mafia.mafia_backend.repository.RoleRefusalTrackerRepository;
import com.mafia.mafia_backend.security.JwtService;
import com.mafia.mafia_backend.service.interfaces.UserServiceInterface;
import com.mafia.mafia_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.mafia.mafia_backend.domain.entity.ConfigSetting;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService implements UserServiceInterface {

    private final UserRepository userRepository;
    private final ConfigSettingRepository configSettingRepository;
    private final RoleRefusalTrackerRepository roleRefusalTrackerRepository;
    private final JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(UserRegistrationDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername()) || dto.getUsername().equals("MafiaBOT")) {
            throw new IllegalArgumentException("Username already taken.");
        }

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already registered.");
        }
        boolean allowMultiple = configSettingRepository
                .findByName("AllowMultipleUsersFromDevice")
                .map(ConfigSetting::isEnabled)
                .orElse(false);

        if (!allowMultiple && userRepository.existsByDeviceInfo(dto.getDeviceInfo())) {
            throw new IllegalArgumentException("A user has already been registered from this device.");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setUserStatus(UserStatus.USER);
        user.setActive(true);
        user.setJoinDate(LocalDateTime.now());
        user.setDeviceInfo(dto.getDeviceInfo());
        user.setMoney(0L);

        System.out.println("OINK! User " + dto.getUsername() + " with email " + dto.getEmail() + " joined the Mafia pigsty!" );
        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    @Override
    public void modifyMoney(Long userId, int delta) {
        User user = getUserById(userId);
        user.setMoney(user.getMoney() + delta);
        userRepository.save(user);
    }

    @Override
    public RoleRefusalTracker getOrCreateTracker(Long userId) {
        return roleRefusalTrackerRepository.findByUserId(userId)
                .orElseGet(() -> {
                    RoleRefusalTracker tracker = new RoleRefusalTracker();
                    tracker.setUserId(userId);
                    return roleRefusalTrackerRepository.save(tracker);
                });
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Missing credentials");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }

        // 🧠 Generate JWT token for this user
        String token = jwtService.generateToken(user.getUsername());

        return Map.of(
                "message", "Login successful",
                "username", user.getUsername(),
                "userId", user.getId(),
                "token", token
        );
    }


    public Map<String, Object> getUserInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername()
        );
    }

}