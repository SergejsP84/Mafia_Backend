package com.mafia.mafia_backend.service.interfaces;

import com.mafia.mafia_backend.domain.model.GameSessionRuntime;

public interface GameRoleAssignerServiceInterface {
    void assignRoles(GameSessionRuntime game);
}
