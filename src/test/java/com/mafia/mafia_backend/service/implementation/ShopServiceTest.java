package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.dto.ShopViewDTO;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.enums.ShopProductCode;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.NightAction;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopServiceTest {

    private GameEconomyService economyService;
    private ShopService shopService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townsfolkRole;

    @BeforeEach
    void setUp() {
        economyService = new GameEconomyService();
        shopService = new ShopService(economyService);
        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townsfolkRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
    }

    @Test
    void pricesAreFixedAndDoNotScaleByPlayerCount() {
        assertEquals(15, ShopProductCode.CONDOM.getPrice());
        assertEquals(30, ShopProductCode.CROSS.getPrice());
        assertEquals(60, ShopProductCode.SILVER.getPrice());
        assertEquals(150, ShopProductCode.ANTIDOTE.getPrice());
        assertEquals(40, ShopProductCode.GHOST_HUNTERS.getPrice());

        GameSessionRuntime small = game(4, List.of(player(1L, "Pig1", townsfolkRole, 500)));
        GameSessionRuntime large = game(50, List.of(player(2L, "Pig2", townsfolkRole, 500)));

        assertEquals(ShopProductCode.CROSS.getPrice(), priceInView(small, 1L, ShopProductCode.CROSS));
        assertEquals(ShopProductCode.CROSS.getPrice(), priceInView(large, 2L, ShopProductCode.CROSS));
    }

    @Test
    void stockScalingUsesProvisionalMonotonicFormula() {
        assertEquals(1, shopService.calculateFiniteStock(4));
        assertEquals(1, shopService.calculateFiniteStock(16));
        assertEquals(2, shopService.calculateFiniteStock(21));
        assertEquals(4, shopService.calculateFiniteStock(50));

        int previous = 0;
        for (int players : List.of(4, 9, 16, 21, 30, 40, 50)) {
            int stock = shopService.calculateFiniteStock(players);
            assertTrue(stock >= previous);
            previous = stock;
        }
    }

    @Test
    void globalStockDecrementsAcrossPlayersAndLastUnitCanOnlyBeBoughtOnce() {
        PlayerInGame one = player(1L, "One", townsfolkRole, 100);
        PlayerInGame two = player(2L, "Two", townsfolkRole, 100);
        GameSessionRuntime game = game(4, List.of(one, two));

        shopService.buy(game, one.getUser().getId(), ShopProductCode.CROSS);

        assertEquals(0, game.getShopState().getProduct(ShopProductCode.CROSS).getRemainingStock());
        assertThrows(IllegalStateException.class,
                () -> shopService.buy(game, two.getUser().getId(), ShopProductCode.CROSS));
        assertEquals(100, two.getInGameMoney());
    }

    @Test
    void onePlayerCanBuyMultipleDifferentProductsButNotDuplicateSameProduct() {
        PlayerInGame buyer = player(1L, "Buyer", townsfolkRole, 200);
        GameSessionRuntime game = game(50, List.of(buyer));

        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CONDOM);
        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CROSS);
        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.SILVER);

        assertTrue(shopService.hasTemporaryItem(buyer, ShopProductCode.CONDOM, 1));
        assertTrue(shopService.hasTemporaryItem(buyer, ShopProductCode.CROSS, 1));
        assertTrue(shopService.hasTemporaryItem(buyer, ShopProductCode.SILVER, 1));
        assertEquals(95, buyer.getInGameMoney());
        assertThrows(IllegalStateException.class,
                () -> shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CROSS));
    }

    @Test
    void personalPaymentDoesNotPropagateToOtherMafiaAndCanDropTier() {
        PlayerInGame mafiaOne = player(1L, "MafiaOne", mafiaRole, 65);
        PlayerInGame mafiaTwo = player(2L, "MafiaTwo", mafiaRole, 65);
        mafiaOne.setTier(2);
        mafiaTwo.setTier(2);
        GameSessionRuntime game = game(16, List.of(mafiaOne, mafiaTwo));

        shopService.buy(game, mafiaOne.getUser().getId(), ShopProductCode.CROSS);

        assertEquals(35, mafiaOne.getInGameMoney());
        assertEquals(1, mafiaOne.getTier());
        assertEquals(65, mafiaTwo.getInGameMoney());
        assertEquals(2, mafiaTwo.getTier());
    }

    @Test
    void insufficientFundsRejectCleanlyWithoutMoneyStockOrOwnershipChanges() {
        PlayerInGame buyer = player(1L, "Buyer", townsfolkRole, 29);
        GameSessionRuntime game = game(16, List.of(buyer));

        assertThrows(IllegalArgumentException.class,
                () -> shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CROSS));

        assertEquals(29, buyer.getInGameMoney());
        assertEquals(1, game.getShopState().getProduct(ShopProductCode.CROSS).getRemainingStock());
        assertFalse(shopService.hasTemporaryItem(buyer, ShopProductCode.CROSS, 1));
        assertTrue(game.getPublicMessages().isEmpty());
    }

    @Test
    void ordinaryPurchasesAreSecretAndDoNotConsumeNightAction() {
        PlayerInGame buyer = player(1L, "Buyer", sheriffRole, 100);
        GameSessionRuntime game = game(16, List.of(buyer));

        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CONDOM);

        assertTrue(game.getPublicMessages().isEmpty());
        assertFalse(buyer.isHasActedTonight());
    }

    @Test
    void temporaryInventorySurvivesUntilNightResolutionFinishesThenExpires() {
        PlayerInGame mafia = player(1L, "Mafia", mafiaRole, 100);
        PlayerInGame sheriff = player(2L, "Sheriff", sheriffRole, 100);
        PlayerInGame town = player(3L, "Town", townsfolkRole, 100);
        GameSessionRuntime game = game(16, List.of(mafia, sheriff, town));

        shopService.buy(game, sheriff.getUser().getId(), ShopProductCode.CROSS);
        assertTrue(shopService.hasTemporaryItem(sheriff, ShopProductCode.CROSS, 1));

        ActionService actionService = actionServiceWithShop();
        actionService.submitNightAction(game, new NightAction(sheriff.getUser().getId(), sheriff.getUser().getId(), NightActionType.CHECK, 1));
        actionService.submitNightAction(game, new NightAction(mafia.getUser().getId(), null, NightActionType.SKIP, 1));
        actionService.resolveNightActions(game, 1);

        assertFalse(shopService.hasTemporaryItem(sheriff, ShopProductCode.CROSS, 1));
    }

    @Test
    void ghostHuntersBookOncePerNightWithoutLeakingBuyerAndAvailableNextNight() {
        PlayerInGame one = player(1L, "One", townsfolkRole, 100);
        PlayerInGame two = player(2L, "Two", townsfolkRole, 100);
        GameSessionRuntime game = game(16, List.of(one, two));

        shopService.buy(game, one.getUser().getId(), ShopProductCode.GHOST_HUNTERS);

        assertEquals(60, one.getInGameMoney());
        assertThrows(IllegalStateException.class,
                () -> shopService.buy(game, two.getUser().getId(), ShopProductCode.GHOST_HUNTERS));
        ShopViewDTO view = shopService.getShopView(game, two.getUser().getId());
        assertFalse(view.products().stream()
                .filter(item -> item.code().equals("GHOST_HUNTERS"))
                .findFirst()
                .orElseThrow()
                .ghostHuntersAvailable());

        game.incrementNightNumber();
        assertTrue(shopService.getShopView(game, two.getUser().getId()).products().stream()
                .filter(item -> item.code().equals("GHOST_HUNTERS"))
                .findFirst()
                .orElseThrow()
                .ghostHuntersAvailable());
    }

    @Test
    void antidoteIsPricedButUnavailableUntilPoisonExists() {
        PlayerInGame buyer = player(1L, "Buyer", townsfolkRole, 500);
        GameSessionRuntime game = game(16, List.of(buyer));

        assertEquals(150, priceInView(game, buyer.getUser().getId(), ShopProductCode.ANTIDOTE));
        assertThrows(IllegalStateException.class,
                () -> shopService.buy(game, buyer.getUser().getId(), ShopProductCode.ANTIDOTE));
        assertEquals(500, buyer.getInGameMoney());
    }

    @Test
    void diggingCanFundShopPurchaseInSameNight() {
        UserRepository userRepository = mock(UserRepository.class);
        PlayerInGame buyer = player(1L, "Buyer", townsfolkRole, 10);
        buyer.getUser().setMoney(600L);
        when(userRepository.findById(buyer.getUser().getId())).thenReturn(Optional.of(buyer.getUser()));
        when(userRepository.saveAndFlush(buyer.getUser())).thenReturn(buyer.getUser());
        GameSessionRuntime game = game(16, List.of(buyer));
        DigService digService = new DigService(userRepository, economyService);

        digService.dig(game, buyer.getUser().getId(), 20);
        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CROSS);

        assertEquals(0, buyer.getInGameMoney());
        assertTrue(shopService.hasTemporaryItem(buyer, ShopProductCode.CROSS, 1));
        assertFalse(buyer.isHasActedTonight());
    }

    @Test
    void vampireProtectionPriorityIsStableSilverBeforeCross() {
        PlayerInGame buyer = player(1L, "Buyer", townsfolkRole, 200);
        GameSessionRuntime game = game(50, List.of(buyer));

        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.CROSS);
        shopService.buy(game, buyer.getUser().getId(), ShopProductCode.SILVER);

        assertEquals(List.of(ShopProductCode.SILVER, ShopProductCode.CROSS),
                shopService.vampireProtectionPriority(buyer, 1));
    }

    @Test
    void shopStateDoesNotLeakBetweenGames() {
        PlayerInGame one = player(1L, "One", townsfolkRole, 100);
        PlayerInGame two = player(2L, "Two", townsfolkRole, 100);
        GameSessionRuntime gameA = game(4, List.of(one));
        GameSessionRuntime gameB = game(4, List.of(two));

        shopService.buy(gameA, one.getUser().getId(), ShopProductCode.CROSS);

        assertEquals(0, gameA.getShopState().getProduct(ShopProductCode.CROSS).getRemainingStock());
        assertEquals(1, shopService.initializeShop(gameB).getProduct(ShopProductCode.CROSS).getRemainingStock());
        assertNotEquals(gameA.getShopState(), gameB.getShopState());
    }

    @Test
    void concurrentFinalUnitPurchaseAllowsOnlyOneBuyer() throws InterruptedException {
        PlayerInGame one = player(1L, "One", townsfolkRole, 100);
        PlayerInGame two = player(2L, "Two", townsfolkRole, 100);
        GameSessionRuntime game = game(4, List.of(one, two));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        Thread first = purchaseThread(start, successes, game, one.getUser().getId(), ShopProductCode.CROSS);
        Thread second = purchaseThread(start, successes, game, two.getUser().getId(), ShopProductCode.CROSS);
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        assertEquals(1, successes.get());
        assertEquals(0, game.getShopState().getProduct(ShopProductCode.CROSS).getRemainingStock());
    }

    @Test
    void concurrentGhostHuntersBookingAllowsOnlyOneBuyer() throws InterruptedException {
        PlayerInGame one = player(1L, "One", townsfolkRole, 100);
        PlayerInGame two = player(2L, "Two", townsfolkRole, 100);
        GameSessionRuntime game = game(16, List.of(one, two));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        Thread first = purchaseThread(start, successes, game, one.getUser().getId(), ShopProductCode.GHOST_HUNTERS);
        Thread second = purchaseThread(start, successes, game, two.getUser().getId(), ShopProductCode.GHOST_HUNTERS);
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        assertEquals(1, successes.get());
        assertEquals(1, game.getShopState().getGhostHuntersBookingsByNight().size());
    }

    private Thread purchaseThread(CountDownLatch start, AtomicInteger successes, GameSessionRuntime game, Long userId, ShopProductCode code) {
        return new Thread(() -> {
            try {
                start.await();
                shopService.buy(game, userId, code);
                successes.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private int priceInView(GameSessionRuntime game, Long userId, ShopProductCode code) {
        return shopService.getShopView(game, userId).products().stream()
                .filter(item -> item.code().equals(code.name()))
                .findFirst()
                .orElseThrow()
                .price();
    }

    private ActionService actionServiceWithShop() {
        ConfigSettingService configSettingService = mock(ConfigSettingService.class);
        when(configSettingService.getIntSetting(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        GameManagerService gameManagerService = mock(GameManagerService.class);
        doAnswer(invocation -> {
            PlayerInGame victim = invocation.getArgument(1);
            victim.setAlive(false);
            return null;
        }).when(gameManagerService).handlePlayerDeath(any(), any(), anyLong());

        ActionService actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "configSettingService", configSettingService);
        ReflectionTestUtils.setField(actionService, "victoryService", new VictoryService());
        ReflectionTestUtils.setField(actionService, "gameManagerService", gameManagerService);
        ReflectionTestUtils.setField(actionService, "privateMessagingService", new PrivateMessagingService());
        ReflectionTestUtils.setField(actionService, "gameEconomyService", economyService);
        ReflectionTestUtils.setField(actionService, "shopService", shopService);
        return actionService;
    }

    private GameSessionRuntime game(int initialPlayerCount, List<PlayerInGame> players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setInitialPlayerCount(initialPlayerCount);
        game.setPlayers(new ArrayList<>(players));
        game.getStageData().put("mafiaOrder", new ArrayList<>(players.stream()
                .filter(player -> player.getRole() != null && player.getRole().getRoleName().equalsIgnoreCase("mafia"))
                .map(player -> player.getUser().getId())
                .toList()));
        game.getStageData().put("currentMafiaIndex", 0);
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));
        return game;
    }

    private PlayerInGame player(Long id, String username, Role role, long inGameMoney) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setMoney(0L);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        player.setAlive(true);
        player.setInGameMoney(inGameMoney);
        player.setTier(economyService.getTierForMoney(gameForTier(), inGameMoney));
        return player;
    }

    private GameSessionRuntime gameForTier() {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.setInitialPlayerCount(16);
        game.getStageData().put("tierThresholds", Map.of(
                "tier2", 60,
                "tier3", 140,
                "tier4", 240
        ));
        return game;
    }
}
