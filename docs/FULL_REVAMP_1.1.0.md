# PlexonJobs 1.1 Full Revamp Audit

Baseline: `v1.0.0` / `24b8e61950cb3a01112351e19c733d9a80953a03`

Target line: `1.1.0-rc.1`

This document records the repository-wide audit required by the Plexon Plugin Full Revamp Standard. The revamp keeps the proven Core-native runtime and stable rollback boundary while addressing player UX, configuration reachability, persistence correctness and maintainability.

## Architecture map

- `PlexonJobs` — lifecycle/composition root, Core integration, reload boundary and shared tasks.
- `runtime` — compiled job registry, Core block-break routing, profiles, daily limits, metrics and shadow accounting.
- `economy` — fixed-minor-unit pending ledger and bounded/coalesced Vault commits.
- `storage` — SQLite/WAL schema and transactional persistence.
- `gui` — player job browsing surface.
- `command` — player/admin entry points.
- `integration` — PlaceholderAPI cache/index-only reads.
- `migration` — fail-closed legacy Jobs discovery/plan tooling.
- `api` / `event` — external PlexonJobs contract.

## Feature viability matrix

| Feature | Player value | Runtime / exploit risk | Decision |
| --- | --- | --- | --- |
| Miner / Woodcutter / Digger | High | Low with Core natural-origin provenance | **Keep** |
| Farmer / Hunter / Fisher / Builder / Crafter / Blacksmith / Brewer / Enchanter / Explorer | Potentially high | High if implemented with duplicate/fallback high-frequency listeners or ambiguous provenance | **Keep definitions disabled** until an authoritative shared context exists |
| Core-native material routing | High | Low; indexed and allocation-bounded | **Keep** |
| SHADOW / PRIMARY / DISABLED rollout | High operational value | Low | **Keep** |
| Coalesced Vault payouts | High | Vault cannot provide cross-process idempotency keys | **Keep**, retain explicit exactly-once limitation |
| SQLite profile/shadow persistence | High | Low after 1.0 shutdown/atomicity fixes | **Keep** |
| Daily money/XP caps | High economy-safety value | Current process-local state resets on restart | **Redesign**: persist same-day counters using the existing `daily_earnings` table without putting SQL on the work-event path |
| `/jobs` browse GUI | High discoverability | Current 54-slot null-holder display is non-interactive and legacy-text based | **Redesign** |
| `/jobs join/leave/info/stats/earnings` | Useful fallback / accessibility | Low | **Keep**, make GUI the primary discoverable flow |
| `/jobs top` placeholder | No current function | Misleading/dead command surface | **Remove** until a real async leaderboard exists |
| `messages.yml` | High admin/localization value | Currently unreachable/dead configuration | **Activate** with fail-closed MiniMessage loading |
| PlaceholderAPI | Useful integration | Low because current reads are cache/index-only | **Keep**, remove hard-coded plugin version |
| Legacy Jobs migration execute | Potentially useful | High data-loss risk without verified source schema | **Keep fail-closed scan/plan only** |
| Admin simulation/diagnostics | High operational value | Simulation already bounded/non-granting | **Keep** |

## Player UX redesign

### Main jobs view

Use a compact 36-slot inventory with a custom `InventoryHolder` and explicit slot actions. All configured jobs remain visible so disabled future job families are understandable rather than silently missing.

Each job icon shows, in order:

1. availability/current membership state;
2. level and XP progression;
3. earned-today state;
4. explicit click hint.

Clicking a job opens a 27-slot detail view. Enabled jobs expose Join/Leave directly. Disabled jobs expose explanation only. Back and close positions remain predictable.

When leaving would reset progression (`keep-level-on-leave: false`), a separate confirmation view is required before mutation.

### Event safety

One listener/router handles the entire menu family. It cancels custom-menu click/drag movement, ignores unknown slots, uses holder/session action identity rather than title/name/lore parsing, and defers inventory transitions to a safe next-tick execution point.

### Text

Player-facing output uses Adventure components and MiniMessage-backed configured templates. Legacy `ChatColor` and regex stripping of MiniMessage markup are removed from player UX.

## Persistence redesign

The existing schema-2 `daily_earnings` table becomes an implemented contract rather than a reserved table.

- Same-day counters are hydrated asynchronously per player.
- Work-event processing remains memory-only and rejects rewards until that player's daily state is ready.
- Mutations mark a player daily snapshot dirty.
- Dirty snapshots are coalesced into the existing periodic persistence cycle.
- Async saves carry a revision; stale completions cannot clear newer dirty state.
- Graceful shutdown waits for in-flight writes and writes final authoritative snapshots.
- Day rollover clears in-memory counters without requiring per-event database access.

No database query/write is added to the block-break hot path.

## Command decisions

The Bukkit command declarations remain for 1.1 because they are small, stable and compatible. Replacing them with Brigadier solely for modernization would add risk without enough player value. The revamp instead makes `/jobs` open the interactive UI, keeps useful direct subcommands as fallback paths, removes the dead `top` branch and keeps permission-aware admin commands.

A future Brigadier migration remains viable if command complexity grows enough to justify it.

## Scheduler / Folia decision

PlexonCore already supplies the async IO and primary-thread marshalling used by persistence. The two periodic Bukkit tasks remain centralized in the plugin lifecycle for this release. PlexonJobs does **not** declare Folia support. A future Folia release must move periodic/player/location work behind a verified scheduler abstraction and receive runtime testing before claiming support.

## Verification gates

The RC is not releasable until all of the following are true:

- Java 25 / Paper 26.2 clean build succeeds.
- Existing runtime/economy/storage tests remain green.
- New daily-cap persistence/restart tests pass.
- New GUI holder/action tests cover identity and navigation contracts where feasible.
- `verifyDistribution` passes and the installable JAR remains reproducible.
- Branch CI reports zero failed/error/skipped tests.
- Stable `v1.0.0` remains the rollback artifact and is never moved/replaced.
- Live promotion remains separate from GitHub source verification and requires PlexonCraft startup, interaction and Spark evidence.
