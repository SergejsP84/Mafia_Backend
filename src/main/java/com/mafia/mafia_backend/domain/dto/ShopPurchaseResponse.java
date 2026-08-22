package com.mafia.mafia_backend.domain.dto;

public record ShopPurchaseResponse(
        String item,
        int price,
        long buyerInGameMoney,
        int buyerTier,
        Integer remainingStock,
        boolean ghostHuntersBooked
) {
}
