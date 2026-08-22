package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.enums.ShopProductCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ShopProductState {
    private ShopProductCode code;
    private boolean enabled;
    private int remainingStock;
}
