package com.mafia.mafia_backend.domain.model;

import java.time.LocalDateTime;

// Represents a single player's bounty offer placed during the CONTRACTS phase.
public class ContractOrder {

    private Long issuerId;     // Player who placed the order
    private Long targetId;     // Target player (may even be issuer himself)
    private int amount;        // Offered bounty (≤ issuer’s current balance)
    private LocalDateTime timestamp; // Time order was placed

    public ContractOrder(Long issuerId, Long targetId, int amount, LocalDateTime timestamp) {
        this.issuerId = issuerId;
        this.targetId = targetId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    // --- getters and setters ---
    public Long getIssuerId() { return issuerId; }
    public void setIssuerId(Long issuerId) { this.issuerId = issuerId; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
