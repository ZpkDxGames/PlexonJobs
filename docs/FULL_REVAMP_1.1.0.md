# PlexonJobs 1.1 Full Revamp Audit

Baseline: `v1.0.0` / `24b8e61950cb3a01112351e19c733d9a80953a03`

Stable target: `1.1.0`

This document records the repository-wide audit required by the Plexon Plugin Full Revamp Standard. The revamp keeps the proven Core-native runtime and previous stable rollback boundary while addressing player UX, configuration reachability, persistence correctness, recovery and maintainability.

## Architecture map

- `PlexonJobs` — lifecycle/composition root, Core integration, reload boundary and shared tasks.
- `runtime` — compiled job registry, Core block-break routing, profiles, daily limits, metrics and shadow accounting.
- `economy` — fixed-minor-unit pending ledger and bounded/coalesced Vault commits.
- `storage` — SQLite/WAL schema and transactional persistence.
- `gui` — holder-identified player job browsing/action surface.
- `command` — player/admin entry points.
- `integration` — PlaceholderAPI cache/index-only reads.
- `migration` — fail-closed legacy Jobs discovery/plan tooling.
- `api` / `event` — external PlexonJobs contract.

## Feature viability matrix

| Feature | Player value | Runtime / exploit risk | Stable decision |
| --- | --- | --- | --- |
| Miner / Woodcutter / Digger | High | Low with Core natural-origin provenance | **Keep** |
| Farmer / Hunter / Fisher / Builder / Crafter / Blacksmith / Brewer / Enchanter / Explorer | Potentially high | High without authoritative shared context | **Keep definitions disabled** |
| Core-native material routing | High | Low; indexed and allocation-bounded | **Keep** |
| SHADOW / PRIMARY / DISABLED rollout | High operational value | Low | **Keep** |
| Coalesced Vault payouts | High | Vault cannot provide cross-process idempotency keys | **Keep**, refresh provider discovery and retain exactly-once limitation |
| SQLite profile persistence | High | Stale rows could survive old UPSERT-only snapshots | **Redesign** to exact transactional snapshots + accepted-registry reconciliation |
| Profile async loading | High | Transient failure previously remained FAILED until restart | **Redesign** with bounded retry/backoff |
| Daily money/XP caps | High economy-safety value | Process-local counters reset on restart in 1.0 | **Redesign** using existing `daily_earnings` table |
| `/jobs` browse GUI | High discoverability | Old null-holder/display-only UI | **Redesign** |
| `/jobs join/leave/info/stats/earnings` | Useful fallback | Low | **Keep** |
| `/jobs top` placeholder | No current function | Misleading/dead surface | **Remove** |
| `messages.yml` | High admin/localization value | Previously unreachable | **Activate** with fail-closed MiniMessage loading |
| PlaceholderAPI | Useful integration | Low; cache/index only | **Keep**, runtime version dynamic |
| Public XP mutation API | Integration value | Negative amount was internally clamped while event reported original value | **Harden**: reject negative, zero no-op |
| Legacy Jobs migration execute | Potentially useful | High data-loss risk without verified source schema | **Keep fail-closed scan/plan only** |
| Admin simulation/diagnostics | High operational value | Simulation bounded/non-granting | **Keep** |
| Dead default config sections | None | Misleading admin surface | **Remove** |

## Player UX redesign

The main jobs view is a compact 36-slot inventory with a custom `InventoryHolder` and explicit slot actions. All configured jobs remain visible so disabled future job families are understandable rather than silently missing.

Each job icon communicates availability/current membership, level/XP progression, earned-today state, and an explicit click hint. Job details use a 27-slot view. Enabled jobs expose Join/Leave directly; disabled jobs expose explanation only. Leaving requires a separate confirmation view when progression would reset.

One listener/router handles the menu family. Custom-menu click/drag movement is cancelled, unknown slots are ignored, behavior identity comes from holder/session actions rather than title/name/lore parsing, and inventory transitions are deferred to a safe server execution point.

Player-facing output uses Adventure components and MiniMessage-backed templates. Legacy `ChatColor` is absent from the 1.1 player/admin surface.

## Persistence and recovery redesign

### Profiles

`player_jobs` is now written as an exact transaction: existing rows for a player are deleted inside the transaction and replaced by the current snapshot. This prevents a removed row from surviving a successful save.

Loaded profiles are reconciled with the accepted job registry. Reconciliation is applied only after a runtime configuration becomes authoritative, so a failed reload cannot prune data according to an unaccepted candidate. Removed IDs advance profile revision and mark the profile dirty so SQLite converges to the accepted definition set.

Transient profile load failures fail closed and retry after bounded backoff instead of remaining permanently FAILED until restart.

### Daily caps

The existing schema-2 `daily_earnings` table is an implemented graceful-restart cap contract.

- Same-day counters hydrate asynchronously per player.
- PRIMARY rewards fail closed until current-day state is ready.
- Work-event processing remains memory-only.
- Dirty snapshots are coalesced through Core IO.
- Async saves carry a revision; stale completions cannot clear newer mutations.
- Graceful shutdown waits for in-flight daily writes and saves final authoritative snapshots.

Abrupt-crash exactness is not claimed because the counters are not synchronously journaled per reward.

### Vault payouts

Payouts remain fixed-minor-unit and coalesced. Vault provider discovery is refreshed before payout flushing, allowing temporary provider loss to recover without a PlexonJobs restart. Retry limits are defined as retry attempts after the initial failed deposit.

Vault still provides no plugin-supplied idempotent transaction identifier, so cross-process exactly-once payout semantics remain explicitly not claimed.

## Command / scheduler decisions

The Bukkit command declarations remain because they are small, stable and compatible. Replacing them with Brigadier solely for modernization would add risk without enough player value.

PlexonCore supplies bounded IO execution and primary-thread marshalling for persistence. Periodic Bukkit tasks remain centralized in plugin lifecycle. PlexonJobs does **not** claim Folia support.

## Stable verification gate

Stable 1.1.0 requires:

- exact previous-stable ancestry;
- Java 25 / Paper 26.2 / verified PlexonCore 2.0.4 build;
- non-empty automated suite with zero failures/errors/skips;
- Javadocs/check/shadowJar/distribution verification;
- hot-path, GUI identity, reload, profile retry, exact snapshot, obsolete-job reconciliation, daily persistence, Vault recovery and public XP-input contracts enforced by tests/source checks;
- installable JAR class major 69 with SQLite included and runtime APIs excluded;
- version exactly `1.1.0` with no RC publisher in the tree;
- merge to `main` through PR #7;
- exact `main` CI success;
- stable publisher run only when `release/stable` equals exact final `main`;
- public `v1.1.0` remote tag and JAR/checksum/test/provenance assets independently verified after publication.

The previous stable rollback is immutable `v1.0.0`. GitHub stable publication is source/CI certification; separate live PlexonCraft host validation is operational follow-up unless explicitly performed.
