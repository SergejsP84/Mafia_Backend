package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.ShopItemViewDTO;
import com.mafia.mafia_backend.domain.dto.ShopPurchaseResponse;
import com.mafia.mafia_backend.domain.dto.ShopViewDTO;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.ShopProductCode;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.GhostHuntersBooking;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.domain.model.ShopProductState;
import com.mafia.mafia_backend.domain.model.ShopState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final GameEconomyService gameEconomyService;

    private static final EnumSet<ShopProductCode> ORDINARY_NIGHT_PRODUCTS = EnumSet.of(
            ShopProductCode.CONDOM,
            ShopProductCode.CROSS,
            ShopProductCode.SILVER,
            ShopProductCode.GHOST_HUNTERS
    );

    public ShopState initializeShop(GameSessionRuntime game) {
        if (game.getShopState() != null) {
            return game.getShopState();
        }

        int initialPlayerCount = requireInitialPlayerCount(game);
        ShopState shop = new ShopState();
        shop.setInitialPlayerCount(initialPlayerCount);

        for (ShopProductCode code : ShopProductCode.values()) {
            boolean enabled = code != ShopProductCode.ANTIDOTE;
            int stock = code.isFiniteStock() && enabled ? calculateFiniteStock(initialPlayerCount) : 0;
            shop.getProducts().put(code, new ShopProductState(code, enabled, stock));
        }

        game.setShopState(shop);
        game.addLog("Shop initialized for " + initialPlayerCount + " players.");
        return shop;
    }

    public int calculateFiniteStock(int initialPlayerCount) {
        return Math.max(1, (int) Math.ceil(initialPlayerCount / 16.0));
    }

    public ShopViewDTO getShopView(GameSessionRuntime game, Long userId) {
        ShopState shop = initializeShop(game);
        PlayerInGame buyer = findBuyer(game, userId);
        int nightNumber = game.getCurrentNightNumber();

        List<ShopItemViewDTO> products = Arrays.stream(ShopProductCode.values())
                .sorted(Comparator.comparing(Enum::ordinal))
                .map(code -> toView(shop, buyer, code, nightNumber))
                .toList();

        return new ShopViewDTO(buyer.getInGameMoney(), buyer.getTier(), products);
    }

    public ShopPurchaseResponse buy(GameSessionRuntime game, Long userId, ShopProductCode code) {
        synchronized (game) {
            ShopState shop = initializeShop(game);
            PlayerInGame buyer = findBuyer(game, userId);
            int nightNumber = game.getCurrentNightNumber();

            validatePurchaseTiming(game, code);
            validateImplemented(code);
            validateEnabled(shop, code);
            validateDuplicatePurchase(buyer, code, nightNumber);
            validateFunds(buyer, code);

            if (code == ShopProductCode.GHOST_HUNTERS) {
                validateGhostHuntersAvailable(shop, nightNumber);
                gameEconomyService.adjustMoney(game, buyer, -code.getPrice(), "Shop purchase: " + code.name());
                shop.getGhostHuntersBookingsByNight().put(nightNumber, new GhostHuntersBooking(nightNumber, userId));
                return new ShopPurchaseResponse(code.name(), code.getPrice(), buyer.getInGameMoney(), buyer.getTier(), null, true);
            }

            ShopProductState product = shop.getProduct(code);
            if (product.getRemainingStock() <= 0) {
                throw new IllegalStateException(code.name() + " is out of stock.");
            }

            gameEconomyService.adjustMoney(game, buyer, -code.getPrice(), "Shop purchase: " + code.name());
            product.setRemainingStock(product.getRemainingStock() - 1);
            buyer.getTemporaryShopItemsByNight().put(code, nightNumber);

            return new ShopPurchaseResponse(
                    code.name(),
                    code.getPrice(),
                    buyer.getInGameMoney(),
                    buyer.getTier(),
                    product.getRemainingStock(),
                    false
            );
        }
    }

    public void expireNightPurchases(GameSessionRuntime game, int nightNumber) {
        if (game == null || game.getShopState() == null) {
            return;
        }

        game.getPlayers().forEach(player ->
                player.getTemporaryShopItemsByNight().entrySet()
                        .removeIf(entry -> entry.getValue() <= nightNumber)
        );
        game.getShopState().getGhostHuntersBookingsByNight().remove(nightNumber);
        game.addLog("Temporary Shop purchases expired for night " + nightNumber + ".");
    }

    public List<ShopProductCode> vampireProtectionPriority(PlayerInGame player, int nightNumber) {
        return List.of(ShopProductCode.SILVER, ShopProductCode.CROSS).stream()
                .filter(code -> hasTemporaryItem(player, code, nightNumber))
                .toList();
    }

    public boolean hasTemporaryItem(PlayerInGame player, ShopProductCode code, int nightNumber) {
        return player.getTemporaryShopItemsByNight().getOrDefault(code, -1) == nightNumber;
    }

    private ShopItemViewDTO toView(ShopState shop, PlayerInGame buyer, ShopProductCode code, int nightNumber) {
        ShopProductState state = shop.getProduct(code);
        boolean ghostHuntersAvailable = code == ShopProductCode.GHOST_HUNTERS
                && state.isEnabled()
                && shop.getGhostHuntersBooking(nightNumber) == null;

        return new ShopItemViewDTO(
                code.name(),
                code.getPrice(),
                state.isEnabled(),
                code.isFiniteStock() ? state.getRemainingStock() : null,
                buyer.getTemporaryShopItemsByNight().getOrDefault(code, -1) == nightNumber,
                buyer.getInGameMoney() >= code.getPrice(),
                code == ShopProductCode.GHOST_HUNTERS ? ghostHuntersAvailable : null
        );
    }

    private int requireInitialPlayerCount(GameSessionRuntime game) {
        if (game == null || game.getInitialPlayerCount() == null || game.getInitialPlayerCount() <= 0) {
            throw new IllegalStateException("Initial player count is required to initialize Shop.");
        }
        return game.getInitialPlayerCount();
    }

    private PlayerInGame findBuyer(GameSessionRuntime game, Long userId) {
        return game.findPlayerById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found in game."));
    }

    private void validatePurchaseTiming(GameSessionRuntime game, ShopProductCode code) {
        if (ORDINARY_NIGHT_PRODUCTS.contains(code) && game.getStage() != GamePhase.NIGHT) {
            throw new IllegalStateException(code.name() + " can only be purchased during NIGHT.");
        }
    }

    private void validateImplemented(ShopProductCode code) {
        if (!code.isPurchasableIn7A()) {
            throw new IllegalStateException(code.name() + " purchase is unavailable until its gameplay mechanic exists.");
        }
    }

    private void validateEnabled(ShopState shop, ShopProductCode code) {
        ShopProductState product = shop.getProduct(code);
        if (product == null || !product.isEnabled()) {
            throw new IllegalStateException(code.name() + " is not enabled in this game.");
        }
    }

    private void validateDuplicatePurchase(PlayerInGame buyer, ShopProductCode code, int nightNumber) {
        if (code != ShopProductCode.GHOST_HUNTERS
                && buyer.getTemporaryShopItemsByNight().getOrDefault(code, -1) == nightNumber) {
            throw new IllegalStateException("Player already purchased " + code.name() + " for this protection cycle.");
        }
    }

    private void validateFunds(PlayerInGame buyer, ShopProductCode code) {
        if (buyer.getInGameMoney() < code.getPrice()) {
            throw new IllegalArgumentException("Insufficient in-game money for " + code.name() + ".");
        }
    }

    private void validateGhostHuntersAvailable(ShopState shop, int nightNumber) {
        if (shop.getGhostHuntersBooking(nightNumber) != null) {
            throw new IllegalStateException("Ghost Hunters are already booked for this night.");
        }
    }
}
