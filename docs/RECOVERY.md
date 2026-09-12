# Recovery and rollback

## Core unavailable
PlexonJobs has a hard dependency on PlexonCore and fails enable. It does not register an independent high-frequency fallback block listener.

## Vault unavailable
Job activity can still be evaluated. In PRIMARY mode the module reports a degraded economy state and pending money remains in the live-process ledger. Coalesced payout flushes refresh Vault provider discovery before deciding availability, so a provider that disappears and later returns can recover without restarting PlexonJobs. `/jobsadmin payout retry` clears blocked retry state; the next flush performs provider discovery again.

`payout.retry-limit` is the number of retry attempts after the initial failed deposit. Vault exposes no plugin-supplied idempotency key, so PlexonJobs does not claim crash-safe cross-process exactly-once payout semantics.

## Invalid configuration / reload
Reload is fail-closed. `config.yml`, `jobs.yml` and `messages.yml` are strictly parsed before the accepted runtime is replaced. Type/range/Core compatibility validation and activity reward compilation complete before the live registry/subscription is swapped. Any failure leaves the previous known-good runtime active.

Daily-cap/timezone changes and 2.0 activity safety/cache/sampling settings require restart. Reward tables, messages and feedback presentation can reload transactionally.

Obsolete job-ID reconciliation occurs only after a new runtime becomes authoritative. A failed reload therefore cannot prune profile data according to an unaccepted definition set.

## Profile load/persistence failure
Profile loads fail closed; rewards are not accepted before a profile is READY. A transient load failure enters FAILED state and is retried after bounded backoff instead of remaining permanently failed until server restart.

`player_jobs` is persisted as an exact transactional snapshot. When an accepted job registry no longer contains a persisted job ID, loaded profiles prune that obsolete ID and become dirty so the stale SQLite row is removed on persistence.

Graceful shutdown stops producers, waits for already-running profile writes, then writes final authoritative snapshots so an older save cannot overwrite newer XP/level state afterward.

## Daily-cap persistence failure
PRIMARY rewards fail closed until the player's persisted current-day cap snapshot has been hydrated. A failed hydration is retried after bounded backoff rather than treating missing state as zero.

Daily counter mutations are memory-first and periodically persisted as **absolute** schema-2 `daily_earnings` snapshots. A failed asynchronous write leaves state dirty for retry; an older successful completion cannot clear a newer dirty revision. Graceful shutdown waits for in-flight daily writes and writes final dirty snapshots before teardown.

This is not a synchronous transaction log: an abrupt host/process crash can lose mutations newer than the most recent completed coalesced snapshot.

## Native activity recovery / safety

### Hunter
Living entities are tagged with their spawn reason when PlexonJobs observes their spawn. Mobs that already existed before plugin/server startup, or whose origin is unknown/custom and not explicitly allowed, fail closed and do not reward Hunter. Default configuration excludes spawner, trial-spawner, spawn-egg, breeding, command and plugin-custom origins.

### Builder
Builder repeat-credit suppression is memory-only, bounded and TTL-based. Restart clears the suppression cache. It is an anti-loop throttle rather than permanent placed-block provenance; configured daily caps remain the persistent economy safety boundary.

### Brewer
Brewing attribution is recent-interaction memory only. A restart or expired attribution causes the batch to earn nothing rather than guessing a player. Automated/unattributed brewing therefore fails closed.

### Explorer
Explorer discoveries are stored in player PDC as a finite environment/biome set. Sampling is periodic and does not depend on movement-event callbacks. Back up player data together with the world if preserving discovery state across world/player-data restoration.

## Feedback failure / cleanup
BossBars and reward sounds are presentation only and do not affect authoritative XP/money state. PlexonJobs keeps at most one mutable active BossBar state per player; expired/offline state is removed by one global cleanup task and all bars are hidden on disable. If feedback is disabled or permission is removed, rewards still process normally.

## SHADOW persistence failure
A drained SHADOW batch is written as one SQLite transaction. A failed batch rolls back as a whole and is requeued as a whole. Graceful shutdown waits for already-running SHADOW writes before final blocking flush.

## Migration
Legacy Jobs data is read-only. Always back up both the legacy source and `plugins/PlexonJobs/jobs.db` before execution. Stable 2.0.0 retains fail-closed scan/plan behavior; it does not guess a proprietary legacy schema.

## Stable rollback for 2.0
The authoritative rollback for `v2.0.0` is stable `v1.1.0`:

- tag: `v1.1.0`
- source: `72f9225c2d337422d617ff2b5638363eeb98a3cf`
- JAR: `PlexonJobs-1.1.0.jar`
- JAR SHA-256: `bbdc7027800029c7588005860befb0f2111cb73352f82aadebce48c3dd594e9f`

Schema remains 2, but take a matching pre-deployment backup of `plugins/PlexonJobs/jobs.db`, `config.yml`, `jobs.yml`, `messages.yml`, `migration.yml`, and relevant player/world data. For operational rollback, stop the server, restore the 1.1.0 JAR and matching configuration/data backup, then start and verify Core/Vault/module state. Do not reverse historical Vault balances automatically.

Historical RC artifacts are provenance only and are not operational rollback targets.

## GitHub closure vs live deployment
Stable `v2.0.0` is published only after the GitHub source/CI gate succeeds on exact merged `main` and the stable workflow independently rebuilds and verifies public artifacts. Release provenance records `release_gate=GITHUB_SOURCE_CI_CERTIFIED`.

Separate live PlexonCraft host certification is operational follow-up. Unless actually performed, provenance records `runtime_certification=NOT_EXECUTED`; this is an explicit scope statement, not a failed GitHub release gate.
