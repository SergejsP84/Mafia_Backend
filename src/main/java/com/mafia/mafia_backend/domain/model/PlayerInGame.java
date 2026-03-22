package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerInGame {
    public PlayerInGame(User user, boolean alive) {
        this.user = user;
        this.alive = alive;
    }

    private User user;                     // Linked user
    private Role role;                     // Assigned role
    private Alignment alignment;           // Current alignment (may change)
    private int tier = 1;                  // Starts at Tier 1
    private long inGameMoney = 0;          // In-game balance (not persistent)
    private LocalDateTime joinedAt = LocalDateTime.now();
    private boolean alive = true;          // Is player still alive?
    private boolean revenant = false;         // Is player a ghost, vampire or demon?
    private boolean hasActedTonight = false;   // Has used night ability?
    private boolean hasSkipped = false;        // Did player skip this round?
    private int skipCount = 0;                 // Number of skips used
    private boolean votedToday = false;        // Has already voted?
    private boolean wasVotedAgainst = false;   // Is currently being voted against?

    private boolean isRecruited = false;       // Used by Mafia Agent recruitment
    private boolean isProtectedTonight = false; // Protected from kill this night?

    private Role roleOffered;
    private boolean roleConfirmed;

    private int refusalsUsed = 0;

    public void incrementRefusalsUsed() {
        refusalsUsed++;
    }
    // Add more booleans or tracking as the game gets more complex
}