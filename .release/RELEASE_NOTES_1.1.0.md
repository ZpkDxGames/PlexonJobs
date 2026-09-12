# PlexonJobs 1.1.0

PlexonJobs 1.1.0 is the stable full-product and architecture revamp of the Plexon-native jobs layer for PlexonCraft.

## Highlights

- Replaces the legacy display-only jobs inventory with compact interactive overview, detail, and leave-confirmation views.
- Uses custom `InventoryHolder` identity and explicit slot actions; titles, display names, and lore are presentation only.
- Activates the Adventure/MiniMessage-backed `messages.yml` surface with safe fallback defaults for older installations.
- Registers the public `PlexonJobsAPI` service correctly during ordinary initial enable.
- Keeps the work-event hot path Core-native, material-indexed, and free of database IO, Vault deposits, YAML parsing, and per-event task creation.
- Persists same-day money/XP cap snapshots through the existing schema-2 `daily_earnings` table with asynchronous hydration and stale-save revision guards.
- Makes profile persistence an exact transactional snapshot and reconciles obsolete job IDs only after a new runtime configuration is accepted.
- Retries transient profile-load failures after bounded backoff instead of leaving a player permanently failed until restart.
- Refreshes Vault provider discovery during coalesced payout flushes so provider outages can recover without restarting PlexonJobs.
- Rejects negative public-API XP mutations and treats zero XP as a non-mutating no-op.
- Removes inert default configuration keys and the dead `/jobs top` placeholder.

## Supported job activity

Miner, Woodcutter, and Digger remain enabled through PlexonCore's authoritative natural-origin block-break context. Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter, and Explorer remain visible but unavailable until an authoritative shared activity context exists. PlexonJobs does not add duplicate high-frequency Bukkit listeners merely to enable unsupported categories.

## Platform

- Paper 26.2 build 121 stable
- Java 25 / class major 69
- PlexonCore 2.0.4 / API 2.x
- Vault API 1.7 with TheosisEconomy as the balance authority
- PlaceholderAPI optional
- SQLite/WAL schema 2

## Persistence and recovery contracts

- PRIMARY rewards fail closed until profile and current-day cap state are ready.
- Profile, daily-cap, and SHADOW persistence remain coalesced outside the work-event path.
- Graceful shutdown establishes write barriers before final authoritative persistence.
- Vault provider recovery is re-evaluated during payout flushes.
- Cross-process exactly-once Vault payout semantics are not claimed because Vault provides no idempotent transaction identifier.
- Daily-cap abrupt-crash exactness is not claimed; same-day snapshots are coalesced rather than synchronously journaled per reward.

## Verification

The stable GitHub release is produced only from the exact merged `main` source. The release workflow rebuilds and tests the source, verifies Java/Paper/PlexonCore provenance, validates stable source contracts, checks the shaded distribution and forbidden runtime APIs, emits checksum/test/provenance evidence, publishes `v1.1.0` as a normal latest release, and verifies the remote tag plus public assets.

Previous stable rollback boundary: `v1.0.0` (`24b8e61950cb3a01112351e19c733d9a80953a03`).
