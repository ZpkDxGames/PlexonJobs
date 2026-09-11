# Changelog

## 1.0.0 — Stable

### Product
- PlexonCore-native job membership, progression, fixed-unit payout accrual, GUI/admin commands, public API/events and PlaceholderAPI integration.
- Miner, Woodcutter and Digger use the authoritative Core natural-origin block-break gateway.
- Job families without authoritative shared contexts remain disabled instead of registering duplicate high-frequency listeners.
- SHADOW / PRIMARY / DISABLED runtime modes, bounded non-granting simulation and fail-closed legacy migration tooling.

### Stable source fixes
- Final profile shutdown persistence now waits for older asynchronous saves before writing the authoritative snapshot, preventing stale XP/level state from landing after shutdown save.
- SHADOW aggregate batches now persist atomically in one SQLite transaction, preventing partial-prefix commit plus whole-batch requeue duplication.
- Graceful shutdown waits for already-running SHADOW persistence before the final blocking shadow flush.
- `config.yml` and `jobs.yml` are strictly parsed before reload can mutate/compile the next runtime; malformed YAML leaves the accepted runtime unchanged.

### Preserved limits
- Vault/TheosisEconomy remains the external balance authority.
- Cross-process exactly-once Vault payout semantics are explicitly **not claimed**, because Vault exposes no plugin-supplied idempotent transaction key.
- Daily cap state remains the documented in-memory active-process/day contract.
- Jobs Reborn migration execution remains fail-closed until an actual source schema is inspected and rehearsed with backup.

### Release boundary
- Accepted RC2 source: `4929145d2e594fef5714319b8f668076fc66498a`.
- Initial stable rollback artifact: immutable `v1.0.0-rc.2`, same source, JAR SHA-256 `082334093b8b9e98afe31593dd4585eb0138ffae730cd1e91e1449cf658f22f6`.
- Stable GitHub publication is exact-current-main gated and verifies downloaded public JAR/checksum/test/provenance assets.
- Live PlexonCraft runtime certification is a deployment follow-up and remains `NOT_EXECUTED` in release provenance until performed.

## 1.0.0 candidate

- Bootstrapped Java 25 / Paper 26.2 Core-native plugin.
- Added API 2.x Core module registration and shared block-break subscriptions.
- Added configuration-driven job registry with all required built-in job IDs.
- Implemented Miner, Woodcutter and Digger on Core natural-origin facts.
- Added in-memory membership, total job XP, levels and daily counters.
- Added fixed-minor-unit reward math, coalesced pending Vault ledger and bounded commits.
- Added SQLite WAL persistence with coalesced saves.
- Added public API and domain events.
- Added `/jobs`, `/jobsadmin`, GUI browsing and PlaceholderAPI integration.
- Added SHADOW/PRIMARY/DISABLED runtime modes.
- Added migration scan/plan safety shell without guessing proprietary legacy schemas.
- Added diagnostics and distribution verification.
- Documented candidate limitations and release/deployment gates.
