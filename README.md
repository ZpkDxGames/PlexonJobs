# PlexonJobs 1.1.0

PlexonJobs is the Plexon-native occupation, job progression and job payout layer for PlexonCraft. It does not own player balances, the general economy, skills, quests, spawners or protection policy.

`1.1.0` is the stable full-product and architecture revamp. Previous stable `v1.0.0` remains the operational rollback boundary.

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

`/jobs` opens a compact interactive jobs browser.

- 36-slot overview and 27-slot detail/confirmation views.
- Explicit `InventoryHolder` identity and slot-to-action routing; titles, names and lore are presentation only.
- One click/drag listener handles the menu family and blocks unsafe transfer paths.
- Inventory transitions caused by clicks are deferred to the next safe server execution point.
- Join/leave actions are available directly from job details.
- Leaving requires confirmation whenever `keep-level-on-leave: false` would reset progression.
- Direct `/jobs leave ...` and `/jobs leaveall` paths require the same explicit `confirm` token when progression would be reset.
- Disabled future job families explain why they are unavailable instead of silently disappearing.
- Player-facing menu/command text uses Adventure/MiniMessage; `messages.yml` is an active configuration surface.

Existing installations may keep an older `messages.yml`; missing 1.1 keys inherit embedded safe defaults.

## Runtime model

Work-event processing is compiled and indexed by material. The block hot path performs no database query/write, no Vault deposit, no task creation and no YAML parsing. Money and XP accrue in memory; player progression, daily-cap snapshots and Vault payout commits are coalesced outside the event path.

`payout.mode` is intentionally `COALESCED` only. `retry-limit` means retry attempts after the initial failed Vault deposit. The payout layer refreshes Vault provider discovery before every coalesced flush so a provider can recover without restarting PlexonJobs.

Money is represented as fixed minor units using deterministic HALF_UP conversion at the configured scale. The pending payout ledger permits only one in-flight aggregate per player, restores failed commits to pending state and prevents duplicate acknowledgement inside one live plugin generation.

Vault does not expose a transaction ID suitable for distributed idempotency. PlexonJobs therefore does **not** claim cross-process exactly-once payout semantics: an abrupt process crash can lose not-yet-flushed memory accrual or leave an externally committed Vault transaction whose local acknowledgement did not complete. Graceful disable attempts to drain pending aggregates and reports any remainder.

## Profiles and persistence

Profiles use generation-bound asynchronous loads and one in-flight save per player.

- Transient profile-load failures fail closed and retry after bounded backoff instead of remaining permanently failed until restart.
- `player_jobs` is persisted as an exact transactional snapshot; removed rows cannot survive a successful save.
- When an accepted runtime removes or renames a job definition, loaded profiles prune obsolete IDs and mark themselves dirty so stale rows are removed from SQLite.
- Reconciliation occurs only after a runtime configuration becomes authoritative, so a failed reload cannot destructively prune data.
- Graceful shutdown waits for older asynchronous profile writes before the final authoritative save.

## Daily caps

The schema-2 `daily_earnings` table is the same-day cap-safety contract.

- Same-day money/XP counters hydrate asynchronously per player.
- PRIMARY reward processing fails closed until the player's current-day snapshot is ready.
- No SQL is added to the work-event hot path.
- Dirty snapshots are coalesced through Core's bounded IO scheduler.
- Snapshot writes are absolute UPSERTs, so retrying the same/newer snapshot cannot double a counter.
- Async saves carry a revision; an older completion cannot clear a newer mutation.
- Graceful shutdown waits for in-flight daily writes and persists final authoritative snapshots.
- GUI, `/jobs earnings` and PlaceholderAPI do not report a false zero while hydration is pending.

Daily-cap abrupt-crash exactness is not claimed; these snapshots are coalesced rather than synchronously journaled per reward.

## Reload and safety contracts

`/jobsadmin reload` is fail-closed. `config.yml`, `jobs.yml` and `messages.yml` are strictly parsed and compiled before the accepted runtime is replaced. Invalid candidates leave the previous runtime active. Daily cap/timezone policy changes remain restart-only so current-day counters cannot be reinterpreted mid-session.

Additional contracts:

- Core provenance `NATURAL` is required for enabled break jobs; placed/unknown origins do not qualify.
- Only configured allowed game modes qualify; default is SURVIVAL.
- Placeholder requests are cache/index-only and perform no database/Vault calls or global job scan.
- SHADOW calculates/coalesces diagnostics without granting job XP or money.
- The public `PlexonJobsAPI` service is registered during ordinary initial enable and reload transitions.
- Public API XP mutations reject negative amounts; zero is a non-mutating no-op.
- PlaceholderAPI reports the actual plugin runtime version.
- SHADOW persistence uses atomic SQLite batches and a graceful-shutdown write barrier.
- Future database schemas fail closed.

## Commands

Player commands: `/jobs`, `/jobs browse`, `/jobs info <job>`, `/jobs join <job>`, `/jobs leave <job> [confirm]`, `/jobs leaveall [confirm]`, `/jobs stats [player]`, `/jobs earnings`.

Admin commands: `/jobsadmin diagnostics`, `/jobsadmin reload`, `/jobsadmin payout retry`, `/jobsadmin migration <scan|plan|status|execute>`, `/jobsadmin simulate <job> <material> <count>`, `/jobsadmin backup`.

The old `/jobs top` placeholder is removed. Simulation is non-granting and bounded to 100,000 actions. Jobs Reborn migration execution remains fail-closed until its actual source schema is inspected and rehearsed against a backup.

## Build and stable release

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

GitHub CI verifies stable `v1.0.0` ancestry, Java 25/Paper 26.2/PlexonCore 2.0.4 provenance, all automated tests, source architecture contracts, the shaded SQLite distribution, absence of accidentally shaded runtime APIs, exact manifest/plugin versioning, and JAR/test/provenance artifacts.

The stable publisher runs only from `release/stable` when that branch points exactly at merged `main`. It rebuilds from source, creates normal release `v1.1.0` (not a prerelease), and verifies the remote tag and public JAR/checksum/test/provenance assets.

Previous stable rollback:

- tag: `v1.0.0`
- source: `24b8e61950cb3a01112351e19c733d9a80953a03`
- JAR: `PlexonJobs-1.0.0.jar`
- SHA-256: `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`

The stable release is GitHub source/CI certified. Separate live PlexonCraft host certification is not implied by the GitHub release evidence.

See `docs/FULL_REVAMP_1.1.0.md`, `docs/PHASE2_ARCHITECTURE.md`, `docs/RECOVERY.md` and `docs/STABLE_RELEASE_GATES.md`.
