# PlexonJobs 1.0.0

PlexonJobs is the Plexon-native occupation, job progression and job payout layer for PlexonCraft. It does not own player balances, the general economy, skills, quests, spawners or protection policy.

## Stable product boundary

The current shared runtime exposes the PlexonCore block-break gateway, so Miner, Woodcutter and Digger are enabled. Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter and Explorer remain disabled until an authoritative shared context/integration exists. PlexonJobs does not register fallback high-frequency Bukkit listeners merely to claim feature coverage.

## Platform

- Paper 26.2 build 121 stable
- Java 25 / class major 69
- PlexonCore 2.0.4, Core API 2.x (`>=2.0 <3.0`)
- Vault API 1.7; TheosisEconomy remains the balance authority through Vault
- PlaceholderAPI optional
- SQLite/WAL persistence, schema 2

## Runtime model

Work-event processing is compiled and indexed by material. The block hot path performs no database query/write, no Vault deposit, no task creation and no YAML parsing. Money and XP accrue in memory; player progression persistence and Vault payout commits are coalesced by shared scheduled work.

`payout.mode` is intentionally `COALESCED` only. The former immediate/per-event path is rejected by strict configuration validation.

Money is represented as fixed minor units using deterministic HALF_UP conversion at the configured scale. The pending payout ledger permits only one in-flight aggregate per player, restores failed commits to pending state and prevents duplicate acknowledgement inside one live plugin generation.

Vault does not expose a transaction ID that PlexonJobs can use for distributed idempotency. PlexonJobs therefore does **not** claim cross-process exactly-once payout semantics: an abrupt process crash can lose not-yet-flushed memory accrual or leave an externally committed Vault transaction whose local acknowledgement did not complete. Graceful disable drains bounded payout aggregates and reports any remainder.

## Persistence and shutdown safety

- Profiles use generation-bound asynchronous loads and one in-flight save per player.
- Graceful shutdown stops event/task producers, waits for already-running profile writes, then writes the final authoritative profile snapshots so an older async save cannot overwrite newer XP/level state.
- SHADOW aggregates persist in one SQLite transaction per drained batch. A failed batch cannot leave a successful prefix committed and then be requeued as a whole.
- Graceful shutdown waits for already-running SHADOW persistence before the final blocking flush.
- SQLite records an explicit schema version and rejects future schemas.

## Reload safety

`/jobsadmin reload` is fail-closed. The candidate `config.yml` and `jobs.yml` are strictly parsed before Bukkit configuration reload/loading is allowed to mutate or compile the next runtime. Invalid or malformed YAML leaves the accepted runtime active. Valid configuration is then type/range validated and compiled before the old Core subscription is retired.

## Other safety contracts

- Core provenance `NATURAL` is required for enabled break jobs; `PLAYER_PLACED`, `UNKNOWN` and disabled origin do not qualify.
- Only configured allowed game modes qualify; default is SURVIVAL.
- Placeholder requests are cache-only/indexed and perform no database/Vault calls or global job scan.
- SHADOW calculates/coalesces diagnostics without granting job XP or money.

## Commands

Player: `/jobs`, `/jobs browse`, `/jobs info <job>`, `/jobs join <job>`, `/jobs leave <job>`, `/jobs leaveall`, `/jobs stats`, `/jobs earnings`.

Admin: `/jobsadmin diagnostics`, `/jobsadmin reload`, `/jobsadmin payout retry`, `/jobsadmin migration <scan|plan|status|execute>`, `/jobsadmin simulate <job> <material> <count>`, `/jobsadmin backup`.

Simulation is non-granting and bounded to 100,000 actions. Migration execution remains fail-closed until the actual Jobs Reborn source schema is inspected and a staging backup rehearsal passes.

## Build and release

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

Stable `1.0.0` is published only from the exact final `main` commit. The Release workflow rebuilds and tests that exact source, publishes `PlexonJobs-1.0.0.jar`, `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then downloads the public assets and verifies their checksum/provenance before succeeding.

GitHub source/release closure does not claim live PlexonCraft certification. Release provenance records `runtime_certification=NOT_EXECUTED` until live deployment validation is performed.

Because the repository has no older stable release, the immutable rollback artifact for initial stable 1.0 is `v1.0.0-rc.2` at `4929145d2e594fef5714319b8f668076fc66498a`, JAR SHA-256 `082334093b8b9e98afe31593dd4585eb0138ffae730cd1e91e1449cf658f22f6`.

See `docs/PHASE2_ARCHITECTURE.md`, `docs/RECOVERY.md` and `docs/STABLE_RELEASE_GATES.md`.
