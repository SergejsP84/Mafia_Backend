package com.mafia.mafia_backend.domain.dto;

public record ShopItemViewDTO(
        String code,
        int price,
        boolean enabled,
        Integer remainingStock,
        boolean alreadyPurchasedThisCycle,
        boolean canAfford,
        Boolean ghostHuntersAvailable
) {
}
