package com.mafia.mafia_backend.controller;

import com.mafia.mafia_backend.domain.dto.ShopPurchaseRequest;
import com.mafia.mafia_backend.domain.dto.ShopPurchaseResponse;
import com.mafia.mafia_backend.domain.dto.ShopViewDTO;
import com.mafia.mafia_backend.domain.enums.ShopProductCode;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.service.implementation.GameManagerService;
import com.mafia.mafia_backend.service.implementation.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final GameManagerService gameManagerService;
    private final ShopService shopService;

    @GetMapping("/{gameId}/{userId}")
    public ResponseEntity<?> view(@PathVariable Long gameId, @PathVariable Long userId) {
        GameSessionRuntime game = gameManagerService.findByGameId(gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Game not found"));
        }

        try {
            ShopViewDTO view = shopService.getShopView(game, userId);
            return ResponseEntity.ok(view);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/{userId}/buy")
    public ResponseEntity<?> buy(
            @PathVariable Long gameId,
            @PathVariable Long userId,
            @RequestBody ShopPurchaseRequest request
    ) {
        if (request == null || request.item() == null || request.item().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Shop item is required."));
        }

        GameSessionRuntime game = gameManagerService.findByGameId(gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Game not found"));
        }

        try {
            ShopProductCode item = ShopProductCode.valueOf(request.item().trim().toUpperCase());
            ShopPurchaseResponse response = shopService.buy(game, userId, item);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
