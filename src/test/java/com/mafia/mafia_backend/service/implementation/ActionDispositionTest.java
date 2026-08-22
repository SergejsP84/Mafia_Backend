package com.mafia.mafia_backend.service.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mafia.mafia_backend.domain.dto.NightActionCatalogDTO;
import com.mafia.mafia_backend.domain.dto.NightActionOptionDTO;
import com.mafia.mafia_backend.domain.entity.Game;
import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.entity.User;
import com.mafia.mafia_backend.domain.enums.ActionDisposition;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.enums.GamePhase;
import com.mafia.mafia_backend.domain.enums.NightActionType;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionDispositionTest {

    private ActionService actionService;
    private Role mafiaRole;
    private Role sheriffRole;
    private Role townRole;

    @BeforeEach
    void setUp() {
        actionService = new ActionService();
        ReflectionTestUtils.setField(actionService, "gameEconomyService", new GameEconomyService());

        mafiaRole = new Role(1L, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer");
        sheriffRole = new Role(2L, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Town investigator");
        townRole = new Role(3L, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Citizen");
    }

    @Test
    void currentNightActionTypesExposeAuthoritativeDisposition() {
        assertEquals(ActionDisposition.DETRIMENTAL, NightActionType.KILL.getDisposition());
        assertEquals(ActionDisposition.NEUTRAL, NightActionType.CHECK.getDisposition());
        assertEquals(ActionDisposition.NONE, NightActionType.SKIP.getDisposition());
    }

    @Test
    void catalogueExposesDispositionForRoleActionsWithoutChangingAvailability() {
        PlayerInGame activeMafia = player(1L, "MafiaOne", mafiaRole, 0);
        PlayerInGame inactiveMafia = player(2L, "MafiaTwo", mafiaRole, 0);
        PlayerInGame sheriff = player(3L, "Sheriff", sheriffRole, 0);
        PlayerInGame town = player(4L, "Town", townRole, 0);
        GameSessionRuntime game = game(activeMafia, inactiveMafia, sheriff, town);

        NightActionCatalogDTO activeMafiaCatalog = actionService.computeActionsFor(game, activeMafia);
        NightActionCatalogDTO inactiveMafiaCatalog = actionService.computeActionsFor(game, inactiveMafia);
        NightActionCatalogDTO sheriffCatalog = actionService.computeActionsFor(game, sheriff);

        assertEquals(1, activeMafiaCatalog.actions().size());
        assertEquals("KILL", activeMafiaCatalog.actions().get(0).code());
        assertEquals(ActionDisposition.DETRIMENTAL, activeMafiaCatalog.actions().get(0).disposition());

        assertTrue(inactiveMafiaCatalog.actions().isEmpty());

        Map<String, NightActionOptionDTO> sheriffActions = sheriffCatalog.actions().stream()
                .collect(Collectors.toMap(NightActionOptionDTO::code, Function.identity()));
        assertEquals(2, sheriffActions.size());
        assertEquals(ActionDisposition.NEUTRAL, sheriffActions.get("CHECK").disposition());
        assertEquals(ActionDisposition.DETRIMENTAL, sheriffActions.get("KILL").disposition());
    }

    @Test
    void digRemainsNonRoleActionAndDoesNotReceiveDisposition() {
        PlayerInGame town = player(1L, "Town", townRole, 90);
        GameSessionRuntime game = game(town);

        NightActionCatalogDTO catalog = actionService.computeActionsFor(game, town);

        NightActionOptionDTO dig = catalog.actions().stream()
                .filter(action -> action.code().equals("DIG"))
                .findFirst()
                .orElseThrow();
        assertNull(dig.disposition());
    }

    @Test
    void dispositionSerializesAsStableEnumName() throws JsonProcessingException {
        PlayerInGame sheriff = player(1L, "Sheriff", sheriffRole, 0);
        GameSessionRuntime game = game(sheriff);

        String json = new ObjectMapper().writeValueAsString(actionService.computeActionsFor(game, sheriff));

        assertTrue(json.contains("\"disposition\":\"NEUTRAL\""));
        assertTrue(json.contains("\"disposition\":\"DETRIMENTAL\""));
        assertFalse(json.contains("\"disposition\":\"BENEFICIAL\""));
    }

    private GameSessionRuntime game(PlayerInGame... players) {
        GameSessionRuntime game = new GameSessionRuntime(null);
        game.setGame(new Game());
        game.advanceStage(GamePhase.NIGHT);
        game.setCurrentNightNumber(1);
        game.setPlayers(new ArrayList<>(List.of(players)));
        game.getStageData().put("mafiaOrder", mafiaOrder(players));
        game.getStageData().put("currentMafiaIndex", 0);
        return game;
    }

    private List<Long> mafiaOrder(PlayerInGame... players) {
        return List.of(players).stream()
                .filter(player -> player.getRole() != null)
                .filter(player -> player.getRole().getRoleName().equalsIgnoreCase("mafia"))
                .map(player -> player.getUser().getId())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private PlayerInGame player(Long id, String username, Role role, long persistentMoney) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setMoney(persistentMoney);

        PlayerInGame player = new PlayerInGame();
        player.setUser(user);
        player.setRole(role);
        player.setAlignment(role.getAlignment());
        player.setAlive(true);
        return player;
    }
}
