# Changelog

## 2.0.0 — Complete jobs and dynamic player feedback

### All built-in jobs
- Enables all 12 built-in job families: Miner, Woodcutter, Digger, Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter and Explorer.
- Preserves PlexonCore as the authoritative high-frequency natural block-break gateway for Miner/Woodcutter/Digger.
- Adds narrow Paper activity adapters for activity types not currently exposed by PlexonCore.
- Generalizes job definitions to indexed activity/key reward maps with exact keys plus optional `*` fallback.
- Routes every activity through one `ActivityGrantService`, keeping runtime mode, profile readiness, daily caps, XP/level events, payouts, metrics and SHADOW semantics consistent.

### Native activity safety
- Farmer rewards mature crops and supported harvest-without-breaking actions.
- Hunter records creature spawn provenance in PDC and fails closed for unknown/disallowed origins; spawner/egg/breeding/command/plugin-custom origins are excluded by default.
- Fisher rewards successful catch results.
- Builder uses a bounded per-player TTL cache to suppress rapid same-position placement farming.
- Crafter consumes Paper post-craft result events.
- Blacksmith rewards furnace extraction, smithing result collection and Mending repairs.
- Brewer attributes completed batches only to recent player interaction with the brewing stand.
- Enchanter rewards successful enchant operations.
- Explorer samples biome/environment discovery periodically, persists discoveries in player PDC and avoids a movement-event hot path.

### Dynamic feedback
- Adds configurable, permission-aware reward BossBars with coalesced XP/money deltas and current level progress.
- Reuses one mutable BossBar per active player; no timer/task is created per reward.
- Adds throttled reward sounds.
- Adds configurable level-up title/subtitle and level-up sound.
- Adds `PlexonJobRewardGrantedEvent` as the post-authoritative-grant presentation/integration event.
- Adds `plexonjobs.feedback`, enabled by default.
- Existing `messages.yml` files inherit embedded defaults for new feedback templates.

### Admin / diagnostics
- `/jobsadmin diagnostics` reports native activity type count and active feedback bars.
- `/jobsadmin simulate <job> <activity> <key> <count>` can simulate any configured 2.0 reward without granting state.
- The 1.1 `/jobsadmin simulate <job> <material> <count>` block-break form remains as a compatibility shortcut.

### Preserved correctness contracts
- Gameplay activity callbacks perform no SQL, Vault deposit, YAML parsing or task creation.
- Profile-load retry/backoff, exact transactional profile snapshots, accepted-runtime job reconciliation, daily snapshot revision guards, SHADOW atomicity and graceful shutdown write barriers remain intact.
- Vault provider discovery remains recoverable during coalesced payout flushing.
- Activity safety/cache/sampling settings are restart-only; reward tables/messages/feedback presentation remain fail-closed reloadable.
- Cross-process exactly-once Vault payout semantics remain explicitly **not claimed**.
- Daily-cap abrupt-crash exactness remains explicitly **not claimed**.

### Stable release boundary
- Version is stable `2.0.0`; no prerelease version or RC publisher is used.
- Stable rollback is `v1.1.0`, source `72f9225c2d337422d617ff2b5638363eeb98a3cf`, JAR SHA-256 `bbdc7027800029c7588005860befb0f2111cb73352f82aadebce48c3dd594e9f`.
- Exact merged-main CI and the stable publisher must both rebuild/test/verify the 2.0 source before `v2.0.0` publication.

## 1.1.0 — Stable full revamp

### Player product / UX
- Replaces the display-only 54-slot jobs inventory with a compact interactive 36-slot overview plus 27-slot details/confirmation flow.
- Uses custom `InventoryHolder` identity and explicit slot actions; titles, names and lore are no longer behavior identity.
- Centralizes click/drag routing, blocks unsafe transfer paths and defers inventory transitions caused by clicks.
- Makes the GUI the primary join/leave discovery surface while preserving direct command fallbacks.
- Requires explicit confirmation before leave/leaveall actions that would reset progression.
- Keeps all configured job families discoverable and removes the non-functional `/jobs top` branch.

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
- The stable publisher accepts only `1.1.0`, only when `release/stable` points exactly at merged `main`, and verifies the remote stable tag plus public JAR/checksum/test/provenance assets.

## 1.0.0 — Stable

- PlexonCore-native job membership, progression, fixed-unit payout accrual, GUI/admin commands, public API/events and PlaceholderAPI integration.
- Miner, Woodcutter and Digger use the authoritative Core natural-origin block-break gateway.
- SHADOW / PRIMARY / DISABLED runtime modes, bounded non-granting simulation and fail-closed legacy migration tooling.
- Final profile shutdown persistence waits for older asynchronous saves before writing the authoritative snapshot.
- SHADOW aggregate batches persist atomically in one SQLite transaction.
- `config.yml` and `jobs.yml` are strictly parsed before reload can mutate/compile the next runtime.
- Final stable `v1.0.0` source: `24b8e61950cb3a01112351e19c733d9a80953a03`.
