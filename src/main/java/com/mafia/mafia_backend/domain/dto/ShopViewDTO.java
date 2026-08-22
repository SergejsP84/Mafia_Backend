package com.mafia.mafia_backend.domain.dto;

import java.util.List;

public record ShopViewDTO(
        long buyerInGameMoney,
        int buyerTier,
        List<ShopItemViewDTO> products
) {
}
