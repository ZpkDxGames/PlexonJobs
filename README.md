# PlexonJobs

PlexonJobs is the Plexon-native occupation, job progression and job payout layer for PlexonCraft. It does not own player balances, the general economy, skills, quests, spawners or protection policy.

## Phase 2 status

`1.0.0` remains a release-candidate line. RC2 hardens the existing Core-native architecture after source audit; stable publication remains blocked on real PlexonCraft runtime certification.

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

Vault does not expose a transaction ID that PlexonJobs can use for distributed idempotency. Therefore PlexonJobs does **not** claim cross-process exactly-once semantics: an abrupt process crash can lose not-yet-flushed memory accrual or leave an externally committed Vault transaction whose local acknowledgement did not complete. Graceful disable drains bounded payout aggregates and reports any remainder.

## Safety

- Core provenance `NATURAL` is required for enabled break jobs; `PLAYER_PLACED`, `UNKNOWN` and disabled origin do not qualify.
- Only configured allowed game modes qualify; default is SURVIVAL.
- Profiles use generation-bound asynchronous loads and one in-flight save per player.
- Config reload parses, validates and compiles the next runtime before the old subscription is retired.
- SQLite records an explicit schema version and rejects future schemas.
- Placeholder requests are cache-only/indexed and perform no database/Vault calls or global job scan.
- SHADOW calculates/coalesces diagnostics without granting job XP or money.

## Commands

Player: `/jobs`, `/jobs browse`, `/jobs info <job>`, `/jobs join <job>`, `/jobs leave <job>`, `/jobs leaveall`, `/jobs stats`, `/jobs earnings`.

Admin: `/jobsadmin diagnostics`, `/jobsadmin reload`, `/jobsadmin payout retry`, `/jobsadmin migration <scan|plan|status|execute>`, `/jobsadmin simulate <job> <material> <count>`, `/jobsadmin backup`.

Simulation is non-granting and bounded to 100,000 actions. Migration execution remains fail-closed until the actual Jobs Reborn source schema is inspected and a staging backup rehearsal passes.

## Build

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

The release workflow publishes an exact-candidate JAR plus `SHA256SUMS.txt`, `TEST_SUMMARY.txt`, and `PROVENANCE.txt`. See `docs/PHASE2_ARCHITECTURE.md` and `docs/STABLE_RELEASE_GATES.md`.
