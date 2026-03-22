package com.mafia.mafia_backend.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "role_refusal_trackers")
public class RoleRefusalTracker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    private Long userId;
    private int sheriffRefusedTimes = 0;
    private int bumRefusedTimes = 0;
    private int doctorRefusedTimes = 0;
    private int schizRefusedTimes = 0;
    private int mafiaRefusedTimes = 0;
    private int lawyerRefusedTimes = 0;
    private int reporterRefusedTimes = 0;
    private int agentRefusedTimes = 0;
    private int maniacRefusedTimes = 0;
    private int hitmanRefusedTimes = 0;
    private int broadRefusedTimes = 0;
    private int hackerRefusedTimes = 0;
    private int bodyguardRefusedTimes = 0;
    private int necroRefusedTimes = 0;
}
