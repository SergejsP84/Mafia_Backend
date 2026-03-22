package com.mafia.mafia_backend.bootstrap;

import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.enums.Alignment;
import com.mafia.mafia_backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        if (roleRepository.count() > 0) return;

        List<Role> roles = List.of(
                // TOWNSFOLK
                new Role(null, "Townsfolk", Alignment.TOWNSFOLK, false, false, false, "Basic citizen with no powers."),
                new Role(null, "Sheriff", Alignment.TOWNSFOLK, true, false, false, "Can kill suspected Mafia."),
                new Role(null, "Bum", Alignment.TOWNSFOLK, false, false, false, "Sheriff's intelligence aide"),
                new Role(null, "Doctor", Alignment.TOWNSFOLK, false, true, false, "Heals people."),
                new Role(null, "Schizophrenic", Alignment.TOWNSFOLK, false, false, false, "Unconventional intel through visions."),
                new Role(null, "Blade", Alignment.TOWNSFOLK, false, false, false, "Hunts the Undead."),

                // MAFIA
                new Role(null, "Mafia", Alignment.MAFIA, true, false, false, "Standard killer."),
                new Role(null, "Lawyer", Alignment.MAFIA, false, false, false, "Provides daytime protection."),
                new Role(null, "Reporter", Alignment.MAFIA, false, false, false, "Deals with information warfare."),
                new Role(null, "Agent", Alignment.MAFIA, false, true, false, "Can kill if guarding target or rejected by a recruit."),

                // NEUTRAL
                new Role(null, "Maniac", Alignment.NEUTRAL, true, false, false, "Lone psycho killer."),
                new Role(null, "Hitman", Alignment.NEUTRAL, true, false, false, "Professional contract killer."),
                new Role(null, "Broad", Alignment.NEUTRAL, false, true, false, "Interferes with the target's actions."),
                new Role(null, "Hacker", Alignment.NEUTRAL, false, false, false, "Steals, discloses and confuses."),
                new Role(null, "Bodyguard", Alignment.NEUTRAL, false, false, false, "Possible protection-based counterattack (TBD)."),

                // UNDEAD
                new Role(null, "Necromancer", Alignment.UNDEAD, true, true, false, "Poisons players and raises Vampires."),
                new Role(null, "Vampire", Alignment.UNDEAD, true, false, true, "Undead killer."),
                new Role(null, "Demon", Alignment.UNDEAD, false, false, true, "Possesses others."),

                // GHOST
                new Role(null, "Ghost", Alignment.GHOST, false, true, true, "Can kill only its murderer.")
        );

        roleRepository.saveAll(roles);
        System.out.println("🐽 Roles seeded successfully.");
    }
}
