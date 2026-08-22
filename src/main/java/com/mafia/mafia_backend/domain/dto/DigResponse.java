package com.mafia.mafia_backend.domain.dto;

public record DigResponse(
        int amountDug,
        long permanentAccountDebited,
        long newPermanentBalance,
        long newInGameBalance,
        int tier,
        boolean digUsed,
        boolean actionCatalogueRefreshRecommended
) {
}
