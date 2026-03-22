package com.mafia.mafia_backend.service.implementation;

import com.mafia.mafia_backend.domain.entity.Role;
import com.mafia.mafia_backend.domain.model.GameSessionRuntime;
import com.mafia.mafia_backend.domain.model.PlayerInGame;
import com.mafia.mafia_backend.repository.RoleRepository;
import com.mafia.mafia_backend.service.interfaces.GameRoleAssignerServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GameRoleAssignerService implements GameRoleAssignerServiceInterface {

    private final RoleRepository roleRepository;

    @Override
    public void assignRoles(GameSessionRuntime game) {
        int playerCount = game.getPlayers().size();

        // Step 1: Get roles based on count
        List<String> roleNames = getRolesForPlayerCount(playerCount);

        // Step 2: Shuffle players
        List<PlayerInGame> players = new ArrayList<>(game.getPlayers());
        Collections.shuffle(players);

        // Step 3: Assign roles
        for (int i = 0; i < roleNames.size(); i++) {
            String roleName = roleNames.get(i);
            Optional<Role> roleOpt = roleRepository.findByRoleName(roleName);
            if (roleOpt.isEmpty()) {
                throw new IllegalStateException("Role not found: " + roleName);
            }

            PlayerInGame player = players.get(i);
            player.setRoleOffered(roleOpt.get());
            player.setRoleConfirmed(false); // Await acceptance

            game.addLog("Role " + roleName + " offered to " + player.getUser().getUsername());
        }
    }

    // Dummy role list for now
    private List<String> getRolesForPlayerCount(int count) {
        if (count == 4) {
            return List.of("Townsfolk", "Townsfolk", "Sheriff", "Mafia");
        }
        throw new UnsupportedOperationException("Role assignment not implemented for " + count + " players yet.");
    }
}