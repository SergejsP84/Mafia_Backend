# Mafia Engine Roadmap

## Doctrine

Do not generalize an interaction before enough roles have been implemented to reveal the actual pattern.
Preserve atomic behavior first; refactor proven repetition later.

## Dependency-Ordered Roadmap

1. Harden the complete 4-player core loop.
   Verify Sheriff CHECK intel delivery, public/private chat, action replacement/cancellation, multiple nights, hanging, both victory checks, dead-player handling, cleanup, and full game completion from lobby to ENDED.

2. Centralize money changes and derived tier calculation.
   Make every reward and penalty go through one money-adjustment service. Tier is always derived from current in-game money and changes immediately upward or downward.

3. Add debug/test controls for money and tiers.
   Allow deliberate Tier 2/3/4 testing without corrupting production thresholds. This is essential before upper-tier abilities and useful for automated Codex tests.

4. Design player-count scaling: Mafia count, remuneration, and tier thresholds together.
   Preserve the reconstructed Mafia staircase provisionally: 1 Mafia at 4 players, 2 at 9, 3 at 15, 4 at 21. Tune thresholds so large games do not make Tier 4 trivial merely because they last longer.

5. Digging for money.
   Finish or verify the existing mechanism. Digging immediately changes in-game money and therefore tier. If Maniac digs into Tier 3 during NIGHT, LAIR becomes available that same night.

6. Action comments.
   Extend submitted actions with an optional bounded comment. Comments travel with the programmed action and are revealed only alongside the corresponding morning result, for example: Mafia laughs, "Squee."

7. Shop core.
   Add nighttime purchasing, finite inventory, immediate money deduction/tier loss, one-night protections, and item availability. Implement infrastructure first; individual items can be added incrementally.

8. Proper communication channels as gameplay state.
   Make Office, Hideout, and Graveyard real membership systems: access, native membership, invitation, banishment, targeting restrictions, and corresponding chats. Membership changes can happen at any time.

9. Shared Office/Hideout knowledge vaults.
   Store persistent perceived intelligence rather than objective truth: subject, believed role, source/night, and history. This must support deliberate misinformation such as Identity Theft, Necromancer Low Profile, and later Agent RECRUIT.

10. Voice functionality.
    Tier 3+ non-Townsfolk roles can emit server-authenticated role-attributed public messages. The client provides only free text; the backend determines the speaker role. Add sensible 256/512-character limits, with anti-spam later.

11. Expand roles vertically, Tier 1 to Tier 4, one role at a time.
    Prefer complete roles over every role at Tier 1. Doctor is the ideal first expansion because HEAL/NIGHTSHIFT immediately exercises interacting night actions. Then add roles in an order that introduces one new mechanic at a time.

12. Grow night-resolution rules atomically with each role.
    Preserve the current fixed role-processing machinery and add targeted behavior inside role processors rather than prematurely redesigning the resolver. Priority effects such as SEDUCE -> CURSE and reactive protections such as GUARD/HEAL/NIGHTSHIFT are added when their roles arrive. Refactor only after the complete interaction patterns are known.

13. Pandora's Box framework.
    Once dependencies exist, add secret random Townsfolk recipient, public discovery announcement, private open/refuse decision, and a preselected outcome. Populate outcomes gradually: money, Shop item, Ambulance Call, Office invitation, MafiaBot RC, Booty Call, poison gas, and so on.

14. Smart/localized message system.
    Replace gameplay prose with semantic message keys and external EN/RU variant pools. Game logic emits an event; messaging chooses one of several localized phrases. Frontend and gameplay logic must not depend on parsing sentence text.

15. Frontend maturation and general chat hardening.
    Keep adding just enough UI for gameplay during development: living/dead lists, channel membership, knowledge displays, shop, comments, Voice. Later do the proper visual overhaul. Add server-side message-size checks and escalating anti-spam/mute behavior for chats.

16. Bots and production systems last.
    Once the rules are stable: AI players with structured state/memory, game/chat archives, permanent bot notes, admin/moderation tools, complaints/reports, production persistence, frontend polish, and deployment hardening.

## Critical Spine

4-player game -> Economy/Tiers -> Scaling -> Dig -> Comments/Shop -> Channels -> Knowledge/Voice -> Complete roles -> Pandora -> Smart text -> Production.

## Near-Term Focus

Do not open a giant new subsystem yet. Finish Line 1 first: verify the Sheriff CHECK fix and inspect whichever remaining defect prevents the 4-player game from being completely trustworthy. If that survives the cannon, commit or tag that state. After that, Line 2 becomes the next real construction project.
