package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.enums.ShopProductCode;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
public class ShopState {
    private int initialPlayerCount;
    private Map<ShopProductCode, ShopProductState> products = new EnumMap<>(ShopProductCode.class);
    private Map<Integer, GhostHuntersBooking> ghostHuntersBookingsByNight = new java.util.HashMap<>();

    public ShopProductState getProduct(ShopProductCode code) {
        return products.get(code);
    }

    public GhostHuntersBooking getGhostHuntersBooking(int nightNumber) {
        return ghostHuntersBookingsByNight.get(nightNumber);
    }
}
