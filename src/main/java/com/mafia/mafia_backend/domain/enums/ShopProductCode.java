package com.mafia.mafia_backend.domain.enums;

public enum ShopProductCode {
    CONDOM(15, true, true),
    CROSS(30, true, true),
    SILVER(60, true, true),
    ANTIDOTE(150, true, false),
    GHOST_HUNTERS(40, false, true);

    private final int price;
    private final boolean finiteStock;
    private final boolean purchasableIn7A;

    ShopProductCode(int price, boolean finiteStock, boolean purchasableIn7A) {
        this.price = price;
        this.finiteStock = finiteStock;
        this.purchasableIn7A = purchasableIn7A;
    }

    public int getPrice() {
        return price;
    }

    public boolean isFiniteStock() {
        return finiteStock;
    }

    public boolean isPurchasableIn7A() {
        return purchasableIn7A;
    }
}
