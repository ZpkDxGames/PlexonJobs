# PlexonJobs 2.0.0

PlexonJobs 2.0.0 completes the PlexonCraft jobs product: every built-in job family now has an authoritative activity path, and successful work can provide coalesced BossBar, sound, and level-up feedback without moving database or Vault work into gameplay callbacks.

## All 12 jobs are live-capable

- **Miner** — natural block breaks through PlexonCore provenance.
- **Woodcutter** — natural log/stem breaks through PlexonCore provenance.
- **Digger** — natural diggable block breaks through PlexonCore provenance.
- **Farmer** — mature crop breaks and supported harvest-without-breaking events.
- **Hunter** — player kills with conservative spawn-origin eligibility; spawner, spawn-egg, breeding, command, plugin-custom, and unknown origins fail closed by default.
- **Fisher** — successful fishing catches, including fish and configured treasure/junk results.
- **Builder** — configured block placements with bounded same-position repeat-credit suppression.
- **Crafter** — Paper post-craft result events using the actual crafted result stack.
- **Blacksmith** — furnace extraction, smithing result collection, Mending repair activity, and real anvil repair/combination output pickup. Rename-only anvil operations are excluded.
- **Brewer** — completed brewing batches attributed to a recent player interaction with that stand; unattributed automation earns nothing.
- **Enchanter** — successful enchant operations, scaled by enchant level cost.
- **Explorer** — first biome/environment discoveries sampled periodically and persisted in player PDC; no movement-event hot path.

## Unified reward pipeline

Every activity flows through one `ActivityGrantService` that owns runtime-mode behavior, world/game-mode policy, profile readiness, daily-cap hydration and clamping, cancellable payout events, XP/level progression, Vault accrual, metrics, SHADOW behavior, and post-grant feedback events.

The 1.1 safety architecture remains intact: gameplay callbacks perform no SQL, no Vault deposit, no YAML parsing, and no task creation.

## Player feedback

2.0 adds configurable, permission-aware dynamic feedback:

- one reusable mutable BossBar per player;
- coalesced XP and money deltas for repeated work;
- current job level/progress on the bar;
- throttled reward sounds;
- configurable level-up title/subtitle and level-up sound;
- one global cleanup tick rather than a timer per reward.

Existing `messages.yml` files inherit safe embedded defaults for the new feedback messages.

## Safety and recovery

- Builder placement-credit memory is TTL-bound and size-bound per player.
- Hunter origin tagging is stored on living entities using plugin PDC and unknown origins fail closed.
- Anvil rewards require the result slot, a real second input, and a non-empty output, preventing rename-only reward farming.
- Brewing attribution is bounded and expires quickly.
- Explorer discoveries are finite and persistent without movement-event listeners.
- Activity cache/sampling safety settings are restart-only; reward tables/messages/feedback presentation remain reloadable.
- Daily-cap persistence, exact profile snapshots, stale-write guards, Vault provider recovery, SHADOW atomicity, and graceful shutdown barriers from 1.1 are retained.

## Platform

- Paper 26.2 build 121 stable
- Java 25 / class major 69
- PlexonCore 2.0.4 / API 2.x
- Vault API 1.7 with TheosisEconomy as balance authority
- PlaceholderAPI optional
- SQLite/WAL schema 2

## Explicit limits

- Cross-process exactly-once Vault payout semantics remain **not claimed** because Vault exposes no idempotency transaction key.
- Daily-cap abrupt-crash exactness remains **not claimed** because same-day counters are coalesced rather than synchronously journaled per reward.
- Hunter mobs created by unsupported/unknown/custom spawn reasons receive no reward unless the administrator explicitly allows that origin.
- Live PlexonCraft host certification is separate operational evidence; GitHub source/CI publication does not fabricate runtime certification.

Rollback boundary: stable `v1.1.0`, source `72f9225c2d337422d617ff2b5638363eeb98a3cf`, JAR SHA-256 `bbdc7027800029c7588005860befb0f2111cb73352f82aadebce48c3dd594e9f`.
