# PlexonJobs 1.1.0-rc.1

PlexonJobs is the Plexon-native occupation, job progression and job payout layer for PlexonCraft. It does not own player balances, the general economy, skills, quests, spawners or protection policy.

`v1.0.0` remains the immutable stable rollback boundary. This branch is the full-revamp `1.1.0-rc.1` candidate and is not live-certified or stable-promoted by source CI alone.

## Product boundary

The current shared runtime exposes the PlexonCore block-break gateway, so Miner, Woodcutter and Digger are enabled. Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter and Explorer remain visible but unavailable until an authoritative shared context/integration exists. PlexonJobs does not register fallback high-frequency Bukkit listeners merely to claim feature coverage.

## Platform

- Paper 26.2 build 121 stable
- Java 25 / class major 69
- PlexonCore 2.0.4, Core API 2.x (`>=2.0 <3.0`)
- Vault API 1.7; TheosisEconomy remains the balance authority through Vault
- PlaceholderAPI optional
- SQLite/WAL persistence, schema 2

## Player UX

`/jobs` now opens a compact interactive jobs browser instead of a display-only 54-slot inventory.

- 36-slot overview and 27-slot detail/confirmation views.
- Explicit `InventoryHolder` identity and slot-to-action routing; titles, names and lore are presentation only.
- One click/drag listener handles the whole menu family and blocks inventory-transfer paths while a custom view is open.
- Inventory transitions caused by clicks are deferred to the next safe server execution point.
- Join/leave actions are available directly from the job detail view.
- Leaving requires confirmation whenever `keep-level-on-leave: false` would reset progression.
- Direct `/jobs leave ...` and `/jobs leaveall` paths require the same explicit `confirm` token when progression would be reset.
- Disabled future job families explain why they are unavailable instead of silently disappearing.
- Player-facing menu/command text uses Adventure/MiniMessage; `messages.yml` is now an active configuration surface.

Existing installations may keep an older `messages.yml`: new 1.1 keys fall back to embedded safe defaults until the administrator chooses to add/override them.

## Runtime model

Work-event processing is compiled and indexed by material. The block hot path performs no database query/write, no Vault deposit, no task creation and no YAML parsing. Money and XP accrue in memory; player progression, daily-cap snapshots and Vault payout commits are coalesced outside the event path.

`payout.mode` is intentionally `COALESCED` only. The former immediate/per-event path is rejected by strict configuration validation.

Money is represented as fixed minor units using deterministic HALF_UP conversion at the configured scale. The pending payout ledger permits only one in-flight aggregate per player, restores failed commits to pending state and prevents duplicate acknowledgement inside one live plugin generation.

Vault does not expose a transaction ID that PlexonJobs can use for distributed idempotency. PlexonJobs therefore does **not** claim cross-process exactly-once payout semantics: an abrupt process crash can lose not-yet-flushed memory accrual or leave an externally committed Vault transaction whose local acknowledgement did not complete. Graceful disable drains bounded payout aggregates and reports any remainder.

## Daily caps and persistence

The existing schema-2 `daily_earnings` table is now an implemented restart-safe cap contract.

- Same-day money/XP counters are hydrated asynchronously per player.
- PRIMARY reward processing fails closed until that player's current-day snapshot is ready.
- No SQL is added to the work-event hot path.
- Mutations remain in memory and mark an absolute snapshot dirty.
- Dirty snapshots are coalesced through Core's bounded IO scheduler.
- Snapshot writes are absolute UPSERTs, so retrying the same/newer snapshot cannot double a counter.
- Async saves carry a revision; an older completion cannot clear a newer mutation.
- Graceful shutdown waits for in-flight daily writes and persists final authoritative snapshots.
- GUI, `/jobs earnings` and PlaceholderAPI never report a false zero while hydration is pending.

The table is a cap-safety contract, not a transaction ledger or financial audit log.

## Other persistence and shutdown safety

- Profiles use generation-bound asynchronous loads and one in-flight save per player.
- Graceful shutdown stops event/task producers, waits for already-running profile writes, then writes the final authoritative profile snapshots so an older async save cannot overwrite newer XP/level state.
- SHADOW aggregates persist in one SQLite transaction per drained batch. A failed batch cannot leave a successful prefix committed and then be requeued as a whole.
- Graceful shutdown waits for already-running SHADOW persistence before the final blocking flush.
- SQLite records an explicit schema version and rejects future schemas.

## Reload safety

`/jobsadmin reload` is fail-closed. Candidate `config.yml`, `jobs.yml` and `messages.yml` are strictly parsed before the accepted runtime is replaced. Invalid or malformed YAML leaves the prior runtime active. Valid configuration is type/range validated and compiled before the old Core subscription is retired.

Daily cap/timezone policy changes remain restart-only so current-day authoritative counters cannot be reinterpreted mid-session.

## Other safety contracts

- Core provenance `NATURAL` is required for enabled break jobs; `PLAYER_PLACED`, `UNKNOWN` and disabled origin do not qualify.
- Only configured allowed game modes qualify; default is SURVIVAL.
- Placeholder requests are cache/index-only and perform no database/Vault calls or global job scan.
- Daily earning placeholders return blank until the persisted same-day snapshot is ready.
- SHADOW calculates/coalesces diagnostics without granting job XP or money.
- The public `PlexonJobsAPI` service is registered during normal initial enable as well as reload transitions.
- PlaceholderAPI reports the actual plugin runtime version rather than a hard-coded release string.

## Commands

Player:

- `/jobs` / `/jobs browse`
- `/jobs info <job>`
- `/jobs join <job>`
- `/jobs leave <job> [confirm]`
- `/jobs leaveall [confirm]`
- `/jobs stats [player]`
- `/jobs earnings`

Admin:

- `/jobsadmin diagnostics`
- `/jobsadmin reload`
- `/jobsadmin payout retry`
- `/jobsadmin migration <scan|plan|status|execute>`
- `/jobsadmin simulate <job> <material> <count>`
- `/jobsadmin backup`

The old `/jobs top` placeholder was removed; a leaderboard will not be exposed until it has a real asynchronous/indexed implementation.

Simulation is non-granting and bounded to 100,000 actions. Migration execution remains fail-closed until the actual Jobs Reborn source schema is inspected and a staging backup rehearsal passes.

## Build and release

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

Branch CI proves the candidate descends from immutable stable `v1.0.0`, provisions the exact verified PlexonCore 2.0.4 API, executes tests, verifies the shaded SQLite distribution, rejects accidentally shaded runtime APIs, and publishes exact JAR/test/provenance artifacts for the tested SHA.

Stable `v1.0.0` is the rollback artifact for this revamp:

- source: `24b8e61950cb3a01112351e19c733d9a80953a03`
- JAR: `PlexonJobs-1.0.0.jar`
- SHA-256: `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`

GitHub source/CI closure does **not** claim live PlexonCraft certification. Startup, GUI interaction regression, restart/cap verification and Spark before/after evidence remain runtime release gates before stable promotion.

See `docs/FULL_REVAMP_1.1.0.md`, `docs/PHASE2_ARCHITECTURE.md`, `docs/RECOVERY.md` and `docs/STABLE_RELEASE_GATES.md`.
