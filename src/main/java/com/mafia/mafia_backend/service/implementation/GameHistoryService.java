package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class GameHistoryService {

    private static final String LOG_DIRECTORY = "game_logs/";

    public void archiveGame(GameSessionRuntime game) {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(LOG_DIRECTORY));

            String fileName = LOG_DIRECTORY + "mafia_game_" +
                    game.getSessionId() + "_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".log";

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                writer.write("=== Mafia Game Summary ===\n");
                writer.write("Game ID: " + game.getSessionId() + "\n");
                writer.write("Winner: " + game.getStageData().get("winnerAlignment") + "\n");
                writer.write("Announcement: " + game.getStageData().get("winnerAnnouncement") + "\n");
                writer.write("Finished: " + LocalDateTime.now() + "\n\n");

                writer.write("=== Final Standings ===\n");
                for (PlayerInGame p : game.getPlayers()) {
                    writer.write(String.format("%s (%s) — $%d, Alive: %s\n",
                            p.getUser().getUsername(),
                            p.getRole().getRoleName(),
                            p.getInGameMoney(),
                            p.isAlive() ? "Yes" : "No"));
                }

                writer.write("\n=== Game Log ===\n");
                for (String entry : game.getLog()) {
                    writer.write(entry + "\n");
                }

                writer.write("\n=== End of Record ===\n");
            }

            game.addLog("🗄️ Game archived to file: " + fileName);
        } catch (IOException e) {
            game.addLog("⚠️ Failed to archive game: " + e.getMessage());
        }
    }
}
