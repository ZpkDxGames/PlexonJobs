# PlexonJobs 2.0.0

PlexonJobs is the Plexon-native occupation, progression, payout, and player-feedback layer for PlexonCraft. `2.0.0` completes every built-in job family while preserving the coalesced persistence/economy architecture established in 1.1.

Stable `v1.1.0` is the rollback boundary for 2.0.

## Platform

- Paper 26.2 build 121 stable
- Java 25 / class major 69
- PlexonCore 2.0.4, Core API 2.x (`>=2.0 <3.0`)
- Vault API 1.7; TheosisEconomy remains the balance authority through Vault
- PlaceholderAPI optional
- SQLite/WAL schema 2

## Built-in jobs

All 12 default job families are enabled:

- **Miner** — natural mining through PlexonCore block provenance.
- **Woodcutter** — natural logs/stems through PlexonCore block provenance.
- **Digger** — natural diggable blocks through PlexonCore block provenance.
- **Farmer** — mature crops and supported harvest-without-breaking actions.
- **Hunter** — player kills with conservative mob spawn-origin eligibility.
- **Fisher** — successful fishing catches and configured treasure/junk.
- **Builder** — configured block placements with repeat-position abuse suppression.
- **Crafter** — successful Paper post-craft result events.
- **Blacksmith** — furnace extraction, smithing, and Mending repair activity.
- **Brewer** — player-attributed completed brewing batches.
- **Enchanter** — successful enchant operations.
- **Explorer** — first biome/environment discoveries sampled periodically.

Miner, Woodcutter, and Digger remain on the shared PlexonCore high-frequency block-break gateway. PlexonJobs does not add a duplicate block-break engine for those jobs. Other activity families use narrow Paper events only where PlexonCore does not currently expose an authoritative shared context.

## Unified reward pipeline

Every activity flows through `ActivityGrantService`. The service owns:

- SHADOW / PRIMARY / DISABLED behavior;
- allowed game mode and disabled-world policy;
- profile readiness and active membership;
- daily-cap hydration, clamping, and commit;
- cancellable `PlexonJobPayoutEvent`;
- XP and level progression/events;
- coalesced Vault accrual;
- SHADOW aggregation and metrics;
- post-grant `PlexonJobRewardGrantedEvent` for presentation.

Gameplay callbacks perform no database query/write, no Vault deposit, no YAML parsing, and no task creation.

## Dynamic player feedback

Successful PRIMARY rewards can show configurable feedback to players with `plexonjobs.feedback`:

- one reusable Adventure BossBar per player;
- XP and money earned coalesced while the bar is visible;
- current level and level-progress bar;
- throttled reward sound;
- level-up title/subtitle;
- separate level-up sound.

Repeated rewards update the same BossBar and refresh its expiry. One global cleanup task handles expiry; PlexonJobs does not schedule one task per reward.

Feedback text lives in `messages.yml`; sound and timing controls live in `config.yml`.

## Activity safety

- **Hunter:** spawn reason is stored on living entities in PDC. Unknown, spawner, trial-spawner, spawn-egg, breeding, command, and plugin-custom origins fail closed unless explicitly allowed by configuration.
- **Builder:** a bounded TTL cache prevents rapid repeated credit at the same position.
- **Brewer:** a completed batch earns only when it can be attributed to a recent player interaction with that brewing stand.
- **Explorer:** discovery is sampled periodically and persisted in player PDC; no `PlayerMoveEvent` listener is used.
- **Crafter:** Paper's post-craft result event is used so the credited result is the item actually taken by the player.

Activity safety/cache/sampling settings are restart-only. Reward tables, messages, and feedback presentation remain safely reloadable.

## Player UX

`/jobs` opens a compact interactive browser:

- 36-slot overview and 27-slot details/confirmation views;
- custom `InventoryHolder` identity and explicit slot actions;
- centralized click/drag protection;
- direct join/leave actions;
- confirmation before progression-resetting leaves;
- Adventure/MiniMessage text through `messages.yml`.

## Persistence and economy

Profiles, daily caps, SHADOW totals, and Vault payouts retain the 1.1 safety contracts:

- transient profile-load failures retry after bounded backoff;
- `player_jobs` saves are exact transactional snapshots;
- obsolete job IDs reconcile only after a runtime configuration is accepted;
- same-day caps hydrate asynchronously and fail closed until ready;
- daily snapshots use revision guards against stale completion;
- SHADOW batches are atomic;
- Vault provider discovery refreshes during payout flushes;
- graceful shutdown establishes final persistence barriers.

Cross-process exactly-once Vault payout semantics are **not claimed** because Vault exposes no idempotent transaction identifier. Daily-cap abrupt-crash exactness is also **not claimed** because snapshots are coalesced rather than synchronously journaled per reward.

## Configuration

`jobs.yml` defines activity rewards by activity name and key. Non-break activities support exact keys and `*` fallback entries. Examples:

```yaml
jobs:
  hunter:
    enabled: true
    kill:
      ZOMBIE: { money: 0.30, xp: 4 }

  explorer:
    enabled: true
    explore:
      "*": { money: 3.00, xp: 40 }
```

`/jobsadmin reload` remains fail-closed: candidate `config.yml`, `jobs.yml`, and `messages.yml` are parsed and compiled before the accepted runtime is replaced.

## Commands

Player: `/jobs`, `/jobs browse`, `/jobs info <job>`, `/jobs join <job>`, `/jobs leave <job> [confirm]`, `/jobs leaveall [confirm]`, `/jobs stats [player]`, `/jobs earnings`.

Admin: `/jobsadmin diagnostics`, `/jobsadmin reload`, `/jobsadmin payout retry`, `/jobsadmin migration <scan|plan|status|execute>`, `/jobsadmin simulate <job> <material> <count>`, `/jobsadmin backup`.

Jobs Reborn migration execution remains fail-closed until its actual source schema is inspected and rehearsed against a backup.

## Build and release

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

Stable publication is exact-main gated. The release workflow rebuilds merged `main`, verifies source contracts and distribution contents, publishes `v2.0.0` as a normal/latest release, then re-downloads the public JAR/checksum/test/provenance assets and verifies them.

See `docs/FULL_RELEASE_2.0.0.md`, `docs/RECOVERY.md`, and `docs/STABLE_RELEASE_GATES.md`.
