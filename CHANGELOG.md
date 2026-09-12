# Changelog

## 1.1.0-rc.1 — Full revamp candidate

### Player product / UX
- Replaces the display-only 54-slot jobs inventory with a compact interactive 36-slot overview plus 27-slot details/confirmation flow.
- Uses custom `InventoryHolder` identity and explicit slot actions; titles, names and lore are no longer behavior identity.
- Centralizes click/drag routing, blocks transfer paths through custom inventories and defers inventory transitions caused by clicks.
- Makes the GUI the primary join/leave discovery surface while preserving useful direct command fallbacks.
- Requires explicit confirmation before any leave/leaveall command or GUI action that would reset progression.
- Keeps all configured job families discoverable; unsupported families explain that they remain unavailable until PlexonCore exposes an authoritative activity context.
- Removes the non-functional `/jobs top` command branch.

### Messages / integration
- Activates `messages.yml` as the configurable Adventure/MiniMessage surface.
- Preserves upgrades from older message files by filling newly introduced keys from embedded defaults.
- Rejects malformed/non-string message configuration before replacing the accepted runtime.
- Removes legacy `ChatColor` player/admin output from the revamp surface.
- Registers the public `PlexonJobsAPI` service during normal initial enable, fixing a lifecycle gap where it was reliably registered only through reload transitions.
- PlaceholderAPI now reports the actual plugin runtime version instead of a hard-coded `1.0.0`.
- Daily-earnings placeholders remain blank until authoritative same-day state is hydrated rather than reporting a false zero.

### Daily-cap persistence
- Activates the existing schema-2 `daily_earnings` table as a graceful-restart-safe same-day cap snapshot store without a schema bump.
- Hydrates current-day counters asynchronously per player; PRIMARY work fails closed until hydration completes.
- Keeps the block-work hot path free from database queries/writes, task creation and YAML parsing.
- Coalesces dirty absolute snapshots through Core IO; retries overwrite absolute values rather than double-add counters.
- Uses per-player revisions so stale asynchronous completions cannot clear newer dirty state.
- Graceful shutdown waits for/finalizes daily snapshots before database teardown.
- Adds diagnostics for daily loaded/loading/saving/dirty state.

### Verification / release discipline
- Starts an isolated `1.1.0-rc.1` line while leaving stable `v1.0.0` and its tag/artifact untouched.
- Branch CI proves ancestry from stable `24b8e61950cb3a01112351e19c733d9a80953a03`, provisions the verified PlexonCore 2.0.4 API, runs tests/build/Javadocs/distribution verification and publishes exact provenance artifacts.
- CI enforces the new menu/persistence classes and rejects legacy `ChatColor`, null-holder GUI creation, title parsing and display-name action identity.
- Adds restart/absolute-write and stale-revision tests for daily caps, explicit holder/action identity tests and old-message-file compatibility/fail-closed tests.
- Live PlexonCraft startup, interaction regression, graceful restart verification, Spark comparison and soak remain required before stable promotion.

### Preserved boundaries
- Miner, Woodcutter and Digger remain the only enabled job families because they have authoritative Core natural-origin block context.
- No duplicate/fallback high-frequency Bukkit work engine was added for unsupported job families.
- Vault/TheosisEconomy remains the external balance authority; cross-process exactly-once Vault payout semantics remain explicitly **not claimed**.
- Jobs Reborn migration execute remains fail-closed until the real production source schema is inspected and rehearsed with backup.
- Stable rollback for this revamp is immutable `v1.0.0`, source `24b8e61950cb3a01112351e19c733d9a80953a03`, JAR SHA-256 `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`.

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
- Daily cap state in 1.0.0 is the documented in-memory active-process/day contract; this limitation is superseded by the 1.1 candidate's persisted graceful-restart snapshots.
- Jobs Reborn migration execution remains fail-closed until an actual source schema is inspected and rehearsed with backup.

### Release boundary
- Accepted RC2 source: `4929145d2e594fef5714319b8f668076fc66498a`.
- Initial stable rollback artifact during the 1.0 publication campaign: immutable `v1.0.0-rc.2`, same source, JAR SHA-256 `082334093b8b9e98afe31593dd4585eb0138ffae730cd1e91e1449cf658f22f6`.
- Final stable `v1.0.0` source: `24b8e61950cb3a01112351e19c733d9a80953a03`.
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
