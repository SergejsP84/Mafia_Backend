package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.PrivateLocationMessageDTO;
import com.mafia.mafia_backend.domain.enums.PrivateLocation;
import com.mafia.mafia_backend.domain.enums.PrivateLocationMessageType;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.model.PrivateLocationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrivateLocationChatService {
    public static final int MAX_MESSAGE_LENGTH = 512;

    private final PrivateLocationService privateLocationService;

    public List<PrivateLocationMessageDTO> getMessages(GameSessionRuntime game, PrivateLocation location, Long requesterId) {
        requireMember(game, location, requesterId);
        return game.getMessagesForPrivateLocation(location).stream()
                .map(this::toDto)
                .toList();
    }

    public PrivateLocationMessageDTO postUserMessage(GameSessionRuntime game, PrivateLocation location, Long senderId, String message) {
        requireMember(game, location, senderId);
        PlayerInGame sender = findPlayer(game, senderId);
        String text = normalizeMessage(message);

        PrivateLocationMessage stored = new PrivateLocationMessage(
                location,
                PrivateLocationMessageType.USER_MESSAGE,
                senderId,
                sender.getUser().getUsername(),
                text,
                LocalDateTime.now());
        game.getMessagesForPrivateLocation(location).add(stored);
        return toDto(stored);
    }

    public void reportAcceptedAction(GameSessionRuntime game, PlayerInGame actor, NightAction action, boolean replacement) {
        if (!isReportable(action)) {
            return;
        }

        PlayerInGame target = findPlayer(game, action.getTargetId());
        for (PrivateLocation location : PrivateLocation.values()) {
            if (privateLocationService.isNativeMember(game, actor.getUser().getId(), location)) {
                String prefix = replacement ? " changed their target and decided to " : " decided to ";
                String text = roleName(actor) + prefix + actionVerb(action.getActionType()) + " " + target.getUser().getUsername() + ".";
                addActionReport(game, location, text);
            }
        }
    }

    public void reportCancelledAction(GameSessionRuntime game, PlayerInGame actor, NightAction previousAction) {
        if (!isReportable(previousAction)) {
            return;
        }

        for (PrivateLocation location : PrivateLocation.values()) {
            if (privateLocationService.isNativeMember(game, actor.getUser().getId(), location)) {
                addActionReport(game, location, roleName(actor) + " cancelled their planned action.");
            }
        }
    }

    private void addActionReport(GameSessionRuntime game, PrivateLocation location, String text) {
        game.getMessagesForPrivateLocation(location).add(new PrivateLocationMessage(
                location,
                PrivateLocationMessageType.ACTION_REPORT,
                null,
                "MafiaBOT",
                text,
                LocalDateTime.now()));
    }

    private boolean isReportable(NightAction action) {
        return action != null
                && action.getTargetId() != null
                && (action.getActionType() == NightActionType.KILL || action.getActionType() == NightActionType.CHECK);
    }

    private void requireMember(GameSessionRuntime game, PrivateLocation location, Long userId) {
        if (!privateLocationService.isMember(game, userId, location)) {
            throw new SecurityException("User is not a member of " + location + ".");
        }
    }

    private PlayerInGame findPlayer(GameSessionRuntime game, Long userId) {
        return game.findPlayerById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Player is not part of this game."));
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message is required.");
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Message cannot be blank.");
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message cannot exceed " + MAX_MESSAGE_LENGTH + " characters.");
        }
        return trimmed;
    }

    private PrivateLocationMessageDTO toDto(PrivateLocationMessage message) {
        return new PrivateLocationMessageDTO(
                message.location().name(),
                message.type().name(),
                message.senderId(),
                message.senderName(),
                message.text(),
                message.timestamp());
    }

    private String roleName(PlayerInGame actor) {
        return actor.getRole() == null ? "Someone" : actor.getRole().getRoleName();
    }

    private String actionVerb(NightActionType actionType) {
        return switch (actionType) {
            case KILL -> "kill";
            case CHECK -> "check";
            case SKIP -> "skip";
        };
    }
}
