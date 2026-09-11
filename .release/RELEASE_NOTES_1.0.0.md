# PlexonJobs 1.0.0

Stable 1.0 promotes the accepted Core-native Jobs runtime and RC2 hardening line to the reproducible stable release channel.

## Included product boundary

- PlexonCore-native Miner, Woodcutter and Digger on authoritative natural-origin block facts.
- Configuration-driven job registry, membership, job XP/levels and fixed-minor-unit reward math.
- Coalesced bounded Vault payouts with TheosisEconomy remaining the external balance authority.
- SHADOW / PRIMARY / DISABLED runtime modes.
- SQLite/WAL schema 2 persistence.
- `/jobs`, `/jobsadmin`, GUI browsing, PlaceholderAPI, public API/events and bounded non-granting simulation.
- Fail-closed legacy migration scan/plan tooling without fabricated Jobs Reborn schema assumptions.

Job families without an authoritative shared event context remain disabled rather than registering duplicate high-frequency Bukkit listeners.

## Stable source fixes after RC2

- **Profile shutdown ordering:** graceful shutdown now waits for already-running asynchronous profile saves before writing final authoritative snapshots, preventing an older XP/level snapshot from landing afterward.
- **Atomic SHADOW persistence:** every drained shadow batch is one SQLite transaction, so a failed batch cannot commit a prefix and then duplicate that prefix when the whole batch is requeued.
- **SHADOW shutdown ordering:** graceful shutdown waits for tracked shadow persistence to settle before the final blocking shadow flush.
- **Fail-closed YAML reload:** malformed `config.yml` or `jobs.yml` is rejected before Bukkit configuration loading can mutate/compile the next runtime; the accepted runtime remains active.

## Explicit payout limitation

Vault exposes no plugin-supplied idempotent transaction key. PlexonJobs therefore does not claim distributed cross-process exactly-once payout semantics. Stable release provenance records `cross_process_vault_exactly_once=NOT_CLAIMED` rather than presenting a local durable ledger as a guarantee it cannot provide.

## Release verification

The stable publisher accepts only `release/stable` at the exact current `main` SHA, proves accepted RC2 ancestry, rebuilds/tests the exact source, verifies Java 25 distribution isolation, publishes the JAR plus checksum/test/provenance evidence, then downloads and verifies those public assets before the workflow can pass.

Live PlexonCraft migration/runtime/Spark/soak certification is separate deployment evidence and is recorded as `runtime_certification=NOT_EXECUTED` at GitHub publication time.

## Rollback

Because PlexonJobs has no older stable GitHub release, initial stable rollback is the immutable final prerelease:

- tag `v1.0.0-rc.2`
- source `4929145d2e594fef5714319b8f668076fc66498a`
- JAR `PlexonJobs-1.0.0-rc.2.jar`
- JAR SHA-256 `082334093b8b9e98afe31593dd4585eb0138ffae730cd1e91e1449cf658f22f6`
