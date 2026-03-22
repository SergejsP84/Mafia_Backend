package com.mafia.mafia_backend.service.implementation;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PrivateMessagingService {

    // key: playerId, value: list of messages sent privately
    private final Map<Long, List<String>> inbox = new ConcurrentHashMap<>();

    public void sendPrivateMessage(Long playerId, String message) {
        inbox.computeIfAbsent(playerId, id -> new ArrayList<>())
                .add(LocalDateTime.now() + " [MafiaBOT → You]: " + message);
    }

    public List<String> getMessagesForPlayer(Long playerId) {
        return inbox.getOrDefault(playerId, Collections.emptyList());
    }

    public void clearMessages(Long playerId) {
        inbox.remove(playerId);
    }
}
