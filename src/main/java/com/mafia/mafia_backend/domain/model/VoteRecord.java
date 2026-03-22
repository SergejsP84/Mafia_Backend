package com.mafia.mafia_backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single voting decision made by a player during the DAY_VOTING phase.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteRecord {
    private Long voterId;
    private Long targetId;          // null if voting for NIGHT
    private boolean voteForNight;   // true if the vote is “night comes”
    private LocalDateTime timestamp;
}
