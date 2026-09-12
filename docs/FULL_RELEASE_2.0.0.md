# PlexonJobs 2.0.0 Full Release Audit

Baseline: stable `v1.1.0` / `72f9225c2d337422d617ff2b5638363eeb98a3cf`

Target: stable `2.0.0` (no prerelease line).

## Product goals

1. Finish every built-in job family: Miner, Woodcutter, Digger, Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter, Explorer.
2. Route every activity through one authoritative grant pipeline so SHADOW/PRIMARY behavior, daily caps, profile readiness, XP/level progression, Vault accrual, metrics, and public events remain identical across job types.
3. Add coalesced player feedback: one reusable BossBar per player, live XP/money deltas, level progress, level-up title/chat feedback, and configurable Adventure sounds.
4. Preserve the 1.1 hot-path and persistence contracts: no database/Vault/YAML work in gameplay event paths, no task-per-event feedback, fail-closed profile/daily state, coalesced payout/persistence, and immutable rollback through `v1.1.0`.

## Activity architecture

### PlexonCore-owned high-frequency block break
- Miner / Woodcutter / Digger continue through PlexonCore `CoreBlockBreakContext` with `requiresNaturalOrigin=true`.
- No duplicate generic block-break engine is added for those families.

### Native Paper activities not currently exposed by PlexonCore
- Farmer: mature crop harvests only. Immature ageable crops do not reward.
- Hunter: player-caused kills, with spawn-origin filtering. Natural/raid/patrol/trap-style combat is eligible; spawner, spawn-egg, breeding, command, and plugin-custom mobs fail closed by default.
- Fisher: successful `PlayerFishEvent` catches.
- Builder: successful block placements with bounded same-position repeat suppression to prevent rapid place/break farming.
- Crafter: completed craft result actions; reward is intentionally per successful craft interaction rather than multiplied by ambiguous shift-craft quantity.
- Blacksmith: furnace extraction, smithing results, and Mending repair activity.
- Brewer: completed brewing batches attributed to the most recent player interaction with that brewing stand; automated/unattributed batches do not reward.
- Enchanter: successful enchant operations.
- Explorer: periodic biome discovery sampling; first discovery per player/environment/biome is stored in player PDC and rewards once without `PlayerMoveEvent`.

## Reward routing

`JobDefinition` becomes activity-aware. Rewards are configured under activity names and indexed by `ActivityType` plus a normalized key (material/entity/action/biome). `*` is an optional fallback key.

All listeners call a central `ActivityGrantService` with:
- player UUID,
- job/activity/key,
- base reward,
- source/provenance string.

The service owns runtime mode, world/game-mode checks, profile membership/readiness, daily hydration/caps, cancellable payout event, XP/level events, payout accrual, metrics, and the post-grant feedback event.

## Feedback architecture

`PlayerFeedbackService` owns at most one mutable Adventure BossBar per player and one global periodic cleanup task. Gameplay events update in-memory state only; they never create timers.

Default reward bar:
- title: job + `+XP` + `+$` delta,
- progress: current level progress,
- duration: configurable,
- repeated activity refreshes/coalesces the existing bar.

Level-up:
- title/subtitle,
- configurable sound,
- bossbar immediately reflects the new level.

Feedback is globally configurable and permission-aware.

## Exploit / performance policy

- Builder repeat-credit cache is bounded and TTL-based; it suppresses rapid same-position loops without database writes.
- Hunter tags spawn reason onto living entities in PDC so kill eligibility survives stacking/delay inside the live process and defaults unknown origins to non-granting.
- Explorer uses a periodic sample rather than `PlayerMoveEvent`.
- Brewing tracks recent stand interaction in bounded memory; unattributed automation earns nothing.
- Crafting deliberately under-rewards ambiguous shift crafting instead of attempting unsafe over-counting.
- No activity listener performs SQL, Vault deposits, YAML parsing, or task creation.

## Release gates

- Full repository build/test/Javadoc/distribution verification on exact branch head.
- Regression tests for activity routing, wildcard reward lookup, anti-repeat cache, feedback state coalescing, and retained 1.1 persistence/economy contracts.
- Merge to `main` preserving provenance only after exact-head branch and PR CI pass.
- Final `main` CI must pass independently.
- Stable `v2.0.0` is built from exact merged `main`, published as normal/latest, and public assets are re-downloaded and checksum-verified.
- Live PlexonCraft runtime certification remains separate operational evidence and is not inferred from GitHub CI.
