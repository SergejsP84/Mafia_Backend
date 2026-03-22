package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.VerdictChoice;
import com.mafia.mafia_backend.domain.model.VerdictVoteRecord;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/votes")
@RequiredArgsConstructor
public class VoteController {

    private final GameManagerService gameManagerService;

    // 🔸 DAY voting
    @PostMapping("/day")
    public ResponseEntity<String> castDayVote(@RequestParam UUID sessionId,
                                              @RequestParam Long voterId,
                                              @RequestParam Long targetId,
                                              @RequestParam(defaultValue = "false") boolean voteForNight) {
        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.notFound().build();

        game.castVote(voterId, targetId, voteForNight);
        game.addLog("Day vote: " + voterId + " → " + targetId);
        return ResponseEntity.ok("Vote cast successfully.");
    }

    // 🔸 Verdict (guilty / innocent)
    @PostMapping("/verdict")
    public ResponseEntity<String> submitVerdictVote(@RequestParam UUID sessionId,
                                                    @RequestParam Long voterId,
                                                    @RequestParam VerdictChoice choice) {
        GameSessionRuntime game = gameManagerService.getGameById(sessionId);
        if (game == null) return ResponseEntity.notFound().build();

        VerdictVoteRecord record = new VerdictVoteRecord(voterId, choice, LocalDateTime.now());
        game.getVerdictVotes().put(voterId, record);
        game.addLog("Verdict vote: " + voterId + " → " + choice);
        return ResponseEntity.ok("Verdict vote recorded.");
    }
}
