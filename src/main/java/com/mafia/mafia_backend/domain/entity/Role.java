package com.mafia.mafia_backend.domain.entity;
import com.mafia.mafia_backend.domain.enums.Alignment;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String roleName;
    @Enumerated(EnumType.STRING)
    private Alignment alignment;
    /**
     * Whether the role is considered capable of *direct* kills under normal conditions.
     * This is used for game flow decisions like "have all killers acted?"
     */
    private boolean canKill;
    /**
     * Whether this role can, under *some* condition, cause death indirectly (e.g. poison, setup, AIDS).
     */
    private boolean canCauseDeath;
    /**
     * Whether this role is transformed or summoned (e.g. Vampires, Ghosts)
     */
    private boolean isSpecialForm;
    /**
     * Optional: Role description for future frontend or help screens
     */
    @Column(length = 2000)
    private String description;
}

