package com.mafia.mafia_backend.domain.model;

import com.mafia.mafia_backend.domain.enums.NightActionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResultRecord {
    private String actingRole;
    private String publicMessage;
    private Long targetId;
    private int moneyChange;
    private NightActionType actionType;
    private Long actorId;
    private String targetRoleName;
}