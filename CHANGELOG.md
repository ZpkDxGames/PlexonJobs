# Changelog

## 1.1.0 — Stable full revamp

### Player product / UX
- Replaces the display-only 54-slot jobs inventory with a compact interactive 36-slot overview plus 27-slot details/confirmation flow.
- Uses custom `InventoryHolder` identity and explicit slot actions; titles, names and lore are no longer behavior identity.
- Centralizes click/drag routing, blocks unsafe transfer paths and defers inventory transitions caused by clicks.
- Makes the GUI the primary join/leave discovery surface while preserving direct command fallbacks.
- Requires explicit confirmation before leave/leaveall actions that would reset progression.
- Keeps all configured job families discoverable; unsupported families explain that they remain unavailable until PlexonCore exposes an authoritative activity context.
- Removes the non-functional `/jobs top` branch.

### Messages / integration
- Activates `messages.yml` as the configurable Adventure/MiniMessage surface.
- Preserves upgrades from older message files by filling newly introduced keys from embedded defaults.
- Rejects malformed/non-string message configuration before replacing the accepted runtime.
- Removes legacy `ChatColor` output from the revamp surface.
- Registers the public `PlexonJobsAPI` service during normal initial enable.
- PlaceholderAPI reports the actual plugin runtime version.
- Daily-earnings placeholders remain blank until authoritative same-day state is hydrated rather than reporting false zero.
- Public API XP mutations reject negative input; zero XP is a non-mutating no-op.

### Persistence / recovery
- Activates schema-2 `daily_earnings` as a graceful-restart-safe same-day cap snapshot store without a schema bump.
- Hydrates current-day counters asynchronously per player; PRIMARY work fails closed until hydration completes.
- Coalesces absolute daily snapshots through Core IO and uses revisions so stale completions cannot clear newer dirty state.
- Retries transient profile-load failures after bounded backoff instead of leaving a player permanently failed until restart.
- Persists `player_jobs` as an exact transactional snapshot.
- Reconciles obsolete/renamed job IDs only after a runtime candidate has been accepted, then marks affected profiles dirty so stale SQLite rows are removed.
- Refreshes Vault economy provider discovery during payout flushes so temporary provider loss can recover without a PlexonJobs restart.
- Graceful shutdown retries provider discovery, settles SHADOW IO, flushes final SHADOW/daily state and then writes final authoritative profiles.

### Verification / release discipline
- Stable version is `1.1.0`; no RC publisher remains in the source tree.
- Branch/PR CI proves ancestry from stable `v1.0.0`, provisions verified PlexonCore 2.0.4, runs tests/build/Javadocs/distribution verification and emits exact provenance artifacts.
- CI enforces holder-based GUI identity, daily persistence, exact profile snapshots, obsolete-job reconciliation, profile retry/backoff, Vault provider recovery and public XP input guards.
- The stable publisher accepts only `1.1.0`, only when `release/stable` points exactly at merged `main`, and verifies the remote stable tag plus public JAR/checksum/test/provenance assets.
- Previous stable rollback remains `v1.0.0`, source `24b8e61950cb3a01112351e19c733d9a80953a03`, JAR SHA-256 `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`.

### Preserved boundaries
- Miner, Woodcutter and Digger remain the only enabled job families because they have authoritative Core natural-origin block context.
- No duplicate/fallback high-frequency Bukkit work engine was added for unsupported job families.
- Vault/TheosisEconomy remains the external balance authority; cross-process exactly-once Vault payout semantics remain explicitly **not claimed**.
- Daily-cap abrupt-crash exactness remains **not claimed** because snapshots are coalesced rather than synchronously journaled per reward.
- Jobs Reborn migration execute remains fail-closed until the real source schema is inspected and rehearsed with backup.

## 1.0.0 — Stable

### Product
- PlexonCore-native job membership, progression, fixed-unit payout accrual, GUI/admin commands, public API/events and PlaceholderAPI integration.
- Miner, Woodcutter and Digger use the authoritative Core natural-origin block-break gateway.
- Job families without authoritative shared contexts remain disabled instead of registering duplicate high-frequency listeners.
- SHADOW / PRIMARY / DISABLED runtime modes, bounded non-granting simulation and fail-closed legacy migration tooling.

### Stable source fixes
- Final profile shutdown persistence waits for older asynchronous saves before writing the authoritative snapshot.
- SHADOW aggregate batches persist atomically in one SQLite transaction.
- Graceful shutdown waits for already-running SHADOW persistence before final blocking flush.
- `config.yml` and `jobs.yml` are strictly parsed before reload can mutate/compile the next runtime.

### Preserved limits
- Vault/TheosisEconomy remains the external balance authority.
- Cross-process exactly-once Vault payout semantics are explicitly **not claimed**.
- Daily cap state in 1.0.0 was an in-memory active-process/day contract; 1.1.0 supersedes it with persisted graceful-restart snapshots.
- Jobs Reborn migration execution remains fail-closed until an actual source schema is inspected and rehearsed with backup.

### Release boundary
- Accepted RC2 source: `4929145d2e594fef5714319b8f668076fc66498a`.
- Final stable `v1.0.0` source: `24b8e61950cb3a01112351e19c733d9a80953a03`.
- Stable GitHub publication is exact-current-main gated and verifies downloaded public JAR/checksum/test/provenance assets.

## 1.0.0 candidate history

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
