# PlexonJobs 2.5.0

PlexonJobs is the Plexon-native occupation, progression, payout and player-feedback system for PlexonCraft. `2.5.0` is a performance-architecture and product-UI stable release built on the `v2.0.0` source boundary.

## Platform

- Paper `26.2.build.121-stable`
- Java 25 / class major 69
- PlexonCore 2.0.4 (`>=2.0 <3.0`)
- Vault API 1.7; PlaceholderAPI optional
- SQLite/WAL schema 2

## Performance architecture

High-frequency activity dispatch is compiled instead of reconstructed per event:

- `CompiledJobRoutes` assigns every accepted job a bit in a 64-bit mask and compiles typed BREAK routes plus exact/wildcard activity routes at startup/reload.
- `ActivityInterestIndex` keeps one compact `PlayerExecutionState` per online player with joined-job and activity masks plus readiness.
- The BREAK path intersects `routeMask & joinedJobMask` before Bukkit player lookup, profile work, daily work, feedback or reward math.
- Paper activity listeners are split by family and are registered only while at least one online participant needs that family.
- PlexonCore block subscription is rebuilt only on topology transitions and contains only the union of materials needed by active online break-job participants.
- Profile and daily hydration start from player lifecycle logic and fail closed until authoritative state is ready.
- Explorer owns no repeating sampler while there are no online Explorer participants.
- Hunter defaults to memory-only origin eligibility; `PERSISTENT_PDC` remains an explicit compatibility/permanence option.

No gameplay callback performs SQL, Vault deposits, YAML parsing or async-task creation.

## Feedback architecture

Authoritative reward code calls an internal `RewardFeedbackSink`; it does not depend on PlexonJobs' own public Bukkit events. Reward callbacks only accumulate compact dirty state. One plugin-level task flushes dirty players at `performance.feedback-flush-ticks` (default 3), reusing one BossBar per player.

Public payout/XP/reward/level events remain API-compatible. The high-frequency pipeline allocates and dispatches them only when their handler list has registered listeners.

## Built-in jobs

All 12 stable job families remain enabled by default: Miner, Woodcutter, Digger, Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter and Explorer. Miner/Woodcutter/Digger continue to use PlexonCore natural-block provenance. Existing daily-cap, SHADOW, persistence, payout, Builder repeat-suppression, Brewer attribution and public API contracts remain in place.

## Premium jobs UI

`/jobs` opens a 45-slot dashboard. The product surface also includes:

- `/jobs browse` — 54-slot paginated browser with All / Joined / Available filters;
- `/jobs profile` — 45-slot profile with combined XP, daily earnings, pending payout and active jobs;
- `/jobs <job>` — 45-slot job details with level/XP, seven-segment progress, daily earnings/caps and join/leave control;
- destructive leave confirmation through Paper's Dialog API when `membership.keep-level-on-leave: false`.

All inventory pages use a custom `InventoryHolder`, explicit slot actions and centralized click/drag routing. Titles, item names and lore are presentation only and are never used as behavior identity. GUIs use already-loaded memory state and show loading/error states instead of fake zeroes.

## Configuration

```yaml
performance:
  dynamic-listeners: true
  dynamic-core-block-subscription: true
  feedback-flush-ticks: 3

activity:
  hunter:
    origin-tracking: MEMORY
```

The tuning surface is intentionally small. `/jobsadmin reload` remains fail-closed: YAML validation, route compilation and candidate topology derivation complete before accepted runtime replacement.

## Diagnostics

`/jobsadmin diagnostics` exposes activity seen/rejection/match/grant counters, family-listener states, Core subscription state/material count, public-event demand gating, feedback accumulations/visual flushes/dirty players, persistence/economy state and Explorer/Hunter status. Per-reward logging is intentionally absent.

## Commands

Player: `/jobs`, `/jobs browse`, `/jobs profile`, `/jobs <job>`, `/jobs info <job>`, `/jobs join <job>`, `/jobs leave <job> [confirm]`, `/jobs leaveall [confirm]`, `/jobs stats [player]`, `/jobs earnings`.

Admin: `/jobsadmin diagnostics`, `/jobsadmin reload`, `/jobsadmin payout retry`, `/jobsadmin migration <scan|plan|status|execute>`, `/jobsadmin simulate <job> <activity> <key> <count>`, `/jobsadmin backup`.

## Build and stable delivery

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

`2.5.0` is stable-only: no RC, prerelease or temporary public candidate tag is part of this release path. GitHub Build freezes the exact branch source, PR CI must pass unchanged, merged `main` is independently rebuilt, `release/stable` must fast-forward exactly to that final `main`, and the stable workflow rebuilds and re-verifies the published assets.

Rollback boundary: `v2.0.0` / `985244c61a3c07c70fb48b97ccb2883fb55149b5` / JAR SHA-256 `686710eed31a6c10e9d78cb7fccc7fdc355330a371a098ba3731940ef048ad0a`.

Live PlexonCraft runtime certification is separate from GitHub source certification. Unless genuine live evidence is produced, provenance states `runtime_certification=NOT_EXECUTED`.

See `docs/PERFORMANCE_ARCHITECTURE_2.5.0.md`, `docs/GUI_2.5.0.md`, `docs/FEATURE_VIABILITY_2.5.0.md`, `docs/STABLE_RELEASE_GATES.md`, `docs/RECOVERY.md`, and `.release/RELEASE_NOTES_2.5.0.md`.
