package com.mafia.mafia_backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerdictVoteRecord {
    private Long voterId;
    private VerdictChoice choice;
    private LocalDateTime timestamp;
}
