# Mafia Backend

A REST API backend for a multiplayer **Mafia** party game, written in Java 17 with Spring Boot 3.5. This is a personal project — work in progress.

## What it is

Players join lobbies, receive secret role assignments, and play through structured day/night game phases. The server manages all game state, enforces phase transitions automatically via a scheduler, handles voting, processes night actions, and determines victory conditions.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 |
| Language | Java 17 |
| Database | MySQL 8 (H2 for tests) |
| ORM | Spring Data JPA / Hibernate |
| Auth | Spring Security + JWT (JJWT 0.11.5) |
| Mapping | MapStruct + Lombok |
| Build | Maven 3 |

## Game Flow

```
LOBBY → ROLE_ASSIGNMENT → NIGHT → DAY_RESULTS → DAY_VOTING
                                       ↑               |
                                       |          LYNCHING → HANGING_DEFENSE
                                       |                          |
                                  (next night)         HANGING_CONFIRMATION
                                                               |
                                                          CONTRACTS
                                                               |
                                                        ENDED / CANCELED
```

**Night actions** (kills, checks, etc.) are *submitted* during the `NIGHT` phase and *resolved in sequence* at the start of `DAY_RESULTS`. The `GamePhaseScheduler` drives transitions automatically.

## Roles & Alignments

Alignments: `TOWNSFOLK`, `MAFIA`, `NEUTRAL`, `UNDEAD`, `GHOST`

Roles are database-seeded at startup via `RoleSeeder` and are fully configurable. Each role carries flags such as `canKill`, `canCauseDeath`, and `isSpecialForm`.

## Project Structure

```
src/main/java/com/mafia/mafia_backend/
├── bootstrap/        # Startup seeders and initializers
├── config/           # Spring Security and CORS config
├── controller/       # 9 REST controllers
├── domain/
│   ├── dto/          # Request/response DTOs
│   ├── entity/       # JPA entities
│   ├── enums/        # GamePhase, Alignment, NightActionType, etc.
│   └── model/        # Runtime domain models (not persisted)
├── exception/        # Global exception handler
├── mappers/          # MapStruct mappers
├── process/          # GamePhaseScheduler (background phase transitions)
├── repository/       # Spring Data JPA repositories
├── security/         # JWT filter and token service
└── service/
    ├── implementation/   # Business logic (GameManagerService, ActionService, etc.)
    └── interfaces/       # Service contracts
```

## Setup

### Prerequisites

- Java 17+
- MySQL 8+
- Maven 3

### Configuration

Copy the example config and fill in your values:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Edit `application.properties`:

```properties
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
JWT_SECRET_KEY=your-secret-key-minimum-32-characters
```

`application.properties` is excluded from version control — never commit it.

### Run

```bash
mvn spring-boot:run
```

The app will seed roles and default config settings on first startup.

## API Overview

| Controller | Responsibility |
|---|---|
| `UserController` | Registration, login |
| `GameController` | Create/join/start games, phase state |
| `ActionController` | Submit and cancel night actions |
| `VoteController` | Day voting mechanics |
| `DefenseController` | Hanging defense phase |
| `ContractsController` | Economy / contract phase |
| `ConfigController` | Game configuration settings |
| `RoleController` | Role catalogue |
| `DebugController` | Dev/testing utilities |

## Notable Design Decisions

A few areas worth looking at if you want to understand how the game is put together:

---

### [VictoryService.java](src/main/java/com/mafia/mafia_backend/service/implementation/VictoryService.java) — Rule-based win conditions

Win conditions are modelled as a list of `VictoryRule` objects, each wrapping a `Predicate<GameSessionRuntime>` and a winner alignment. Evaluating the game is a single `rules.stream().filter(...).findFirst()`. Adding a new win condition means appending a rule — no branching in the caller, no switch statement to maintain.

---

### [ActionService.java — `resolveNightActions()`](src/main/java/com/mafia/mafia_backend/service/implementation/ActionService.java) — The lore/mechanics split

Lore-wise, actions happen at night. Technically, they are only *queued* during `NIGHT` — the actual resolution fires sequentially at the start of `DAY_RESULTS`. Resolution is ordered by role (Sheriff first, then the active Mafia soldier determined by a rotating `mafiaOrder` index), each producing a `ResultRecord` that drives both the public announcement and the in-game effect (money changes, kills, private intelligence).

---

### [GameSessionRuntime.java](src/main/java/com/mafia/mafia_backend/domain/model/GameSessionRuntime.java) — Thread-safe in-memory game state

The entire live game state lives in a single runtime object — no round-tripping to the database mid-game. Highlights:
- `ConcurrentHashMap<Long, LocalDateTime> lastVoteTimestamps` — per-voter rate limiting without a separate service
- `synchronized beginPhaseAdvance()` / `endPhaseAdvance()` — a lightweight mutex preventing two scheduler ticks from advancing the phase simultaneously
- `addNightAction()` replaces any existing action from the same actor, making resubmission naturally idempotent
- Contract bounty aggregation via `getAggregatedContracts()` — issuers commit intent but money isn't deducted until resolution

---

### [GamePhaseScheduler.java](src/main/java/com/mafia/mafia_backend/process/GamePhaseScheduler.java) — Automated phase transitions

A `@Scheduled` component polls all active sessions every 5 seconds and advances any that have timed out or met their completion criteria (all votes in, all night actions submitted, etc.). Phase durations come from `ConfigSettingService` so they can be tuned without a redeploy.

---

## Status

Work in progress. Core game loop is functional; some phases and features are still being built out.
