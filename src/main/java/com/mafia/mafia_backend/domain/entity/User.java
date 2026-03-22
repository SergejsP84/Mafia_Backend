package com.mafia.mafia_backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mafia.mafia_backend.domain.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username is required")
    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "is_active")
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "join_date", updatable = false)
    private LocalDateTime joinDate;

    @Column(name = "money")
    private Long money = 0L;

    @NotNull
    @JsonIgnore
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, and one digit"
    )
    @Column(name = "password")
    private String password;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UserStatus userStatus;

    @NotBlank(message = "Email is required")
    @Column(name = "email", unique = true)
    @Email
    private String email;

    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;


}
