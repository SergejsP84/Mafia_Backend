package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.PrivateLocationKnowledgeDTO;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.model.PrivateLocationKnowledgeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrivateLocationKnowledgeVaultService {

    private final PrivateLocationService privateLocationService;

    public void recordKnowledge(GameSessionRuntime game, PrivateLocation location, Long subjectUserId, String perceivedRole) {
        if (subjectUserId == null) {
            throw new IllegalArgumentException("Subject user id is required.");
        }
        String normalizedRole = normalizeRole(perceivedRole);
        game.getKnowledgeVaultForPrivateLocation(location)
                .put(subjectUserId, new PrivateLocationKnowledgeRecord(subjectUserId, normalizedRole));
    }

    public Optional<PrivateLocationKnowledgeRecord> getKnownRole(
            GameSessionRuntime game,
            PrivateLocation location,
            Long subjectUserId
    ) {
        return Optional.ofNullable(game.getKnowledgeVaultForPrivateLocation(location).get(subjectUserId));
    }

    public List<PrivateLocationKnowledgeDTO> getKnowledgeForMember(
            GameSessionRuntime game,
            PrivateLocation location,
            Long requesterId
    ) {
        requireMember(game, location, requesterId);
        return game.getKnowledgeVaultForPrivateLocation(location).values().stream()
                .sorted(Comparator.comparing(PrivateLocationKnowledgeRecord::subjectUserId))
                .map(record -> toDto(game, record))
                .toList();
    }

    public void removeKnowledgeAboutPlayer(GameSessionRuntime game, Long subjectUserId) {
        if (subjectUserId == null) {
            return;
        }
        for (PrivateLocation location : PrivateLocation.values()) {
            game.getKnowledgeVaultForPrivateLocation(location).remove(subjectUserId);
        }
    }

    private void requireMember(GameSessionRuntime game, PrivateLocation location, Long userId) {
        if (!privateLocationService.isMember(game, userId, location)) {
            throw new SecurityException("User is not a member of " + location + ".");
        }
    }

    private PrivateLocationKnowledgeDTO toDto(GameSessionRuntime game, PrivateLocationKnowledgeRecord record) {
        String playerName = game.findPlayerById(record.subjectUserId())
                .map(PlayerInGame::getUser)
                .map(user -> user.getUsername())
                .orElse("Unknown");

        return new PrivateLocationKnowledgeDTO(record.subjectUserId(), playerName, record.perceivedRole());
    }

    private String normalizeRole(String perceivedRole) {
        if (perceivedRole == null) {
            throw new IllegalArgumentException("Perceived role is required.");
        }
        String trimmed = perceivedRole.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Perceived role cannot be blank.");
        }
        return trimmed;
    }
}
