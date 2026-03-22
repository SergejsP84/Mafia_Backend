package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

@Service
@Getter
@Setter
public class VictoryService {

    // Represents one win condition rule

    @Getter
    @Setter
    public static class VictoryRule {
        private final Predicate<GameSessionRuntime> condition;
        private final Alignment winner;
        private final String announcement;
        private boolean isDraw = false;
        private boolean allDead = false;

        public VictoryRule(Predicate<GameSessionRuntime> condition,
                           Alignment winner,
                           String announcement, boolean isDraw, boolean allDead) {
            this.condition = condition;
            this.winner = winner;
            this.announcement = announcement;
            this.isDraw = isDraw;
            this.allDead = allDead;
        }

        public boolean allDead() {
            return this.allDead;
        }
    }

    private final List<VictoryRule> rules = new ArrayList<>();

    public VictoryService() {
        registerDefaultRules();
    }

    private void registerDefaultRules() {
        // 🏆 Rule 1: Town wins if no Mafia or other killers remain
        rules.add(new VictoryRule(
                game -> {
                    List<PlayerInGame> alive = game.getPlayers().stream()
                            .filter(PlayerInGame::isAlive)
                            .toList();

//                    boolean mafiaAlive = alive.stream()
//                            .anyMatch(p -> p.getRole().getAlignment() == Alignment.MAFIA);
                    boolean atLeastOneTownsfolkPresent = alive.stream()
                            .filter(p -> p.getAlignment().equals(Alignment.TOWNSFOLK)).count() > 0;

                    boolean otherKillersAlive = alive.stream()
                            .anyMatch(p -> p.getRole().getAlignment() != Alignment.TOWNSFOLK
                                    && p.getRole().isCanKill());

                    boolean enoughTownsfolkForHanging = alive.stream()
                            .filter(p -> p.getAlignment().equals(Alignment.TOWNSFOLK)).count() > alive.stream()
                            .filter(p -> !p.getAlignment().equals(Alignment.TOWNSFOLK)).count();

                    return atLeastOneTownsfolkPresent && !otherKillersAlive && enoughTownsfolkForHanging;
                },
                Alignment.TOWNSFOLK,
                "🌅 Justice triumphs! The Mafia and all killers are gone — peace returns to Mafsville!", false, false
        ));

        // 🏆 Rule 2: Mafia wins if no other killers remain
        rules.add(new VictoryRule(
                game -> {
                    List<PlayerInGame> alive = game.getPlayers().stream()
                            .filter(PlayerInGame::isAlive)
                            .toList();

                    boolean mafiaAlive = alive.stream()
                            .anyMatch(p -> p.getRole().getAlignment() == Alignment.MAFIA);

                    boolean otherKillersAlive = alive.stream()
                            .anyMatch(p -> !p.getRole().getAlignment().equals(Alignment.MAFIA)
                                    && p.getRole().isCanKill());

                    boolean mafiaHasMajority = alive.stream()
                            .filter(p -> p.getAlignment() == Alignment.MAFIA).count()
                            > alive.stream()
                            .filter(p -> p.getAlignment() != Alignment.MAFIA).count();

                    return mafiaAlive && !otherKillersAlive && mafiaHasMajority;
                },
                Alignment.MAFIA,
                "💀 Darkness prevails! Mafsville falls under the control of the underworld.", false, false
        ));

        // 🏆 Rule 3: Specific draw game conditions - just one by now, with 1 Mafia and the Sheriff left in game
        rules.add(new VictoryRule(
                game -> {
                    List<PlayerInGame> alive = game.getPlayers().stream()
                            .filter(PlayerInGame::isAlive)
                            .toList();

                    boolean justTwoKillersLeft = (alive.size() == 2) &&
                            (alive.get(0).getRole().isCanKill() && alive.get(1).getRole().isCanKill()) &&
                            (!alive.get(0).getAlignment().equals(alive.get(1).getAlignment()));

                    // Will supplement to handle specific cases when one of the roles might be unable
                    // to kill the other due to low tier, possible use of defensive abilities, etc.

                    return justTwoKillersLeft;
                },
                Alignment.NONE,
                "The game is a draw! Well... everyone could have fared better this time.", true, false
        ));

        // 🏆 Rule 4: For cases when no one is left - hardly achievable now, but possible with greater number of players and roles
        rules.add(new VictoryRule(
                game -> {
                    List<PlayerInGame> alive = game.getPlayers().stream()
                            .filter(PlayerInGame::isAlive)
                            .toList();

                    List<PlayerInGame> undead = game.getPlayers().stream()
                            .filter(p -> (p.getRole().getRoleName().toLowerCase().equals("vampire") ||
                                    p.getRole().getRoleName().toLowerCase().equals("demon"))).toList();

                    boolean absolutelyEveryoneDied = alive.isEmpty() && undead.isEmpty();

                    return absolutelyEveryoneDied;
                },
                Alignment.NONE,
                "The game is over! Everyone died! The once shiny town of Mafsville has been reduced to a pile of smoldering ruins...", false, true
        ));
    }

    /**
     * Evaluates all registered victory rules for the given game.
     * Returns the first matching rule (if any).
     */
    public Optional<VictoryRule> evaluate(GameSessionRuntime game) {
        return rules.stream()
                .filter(rule -> rule.condition.test(game))
                .findFirst();
    }
}

