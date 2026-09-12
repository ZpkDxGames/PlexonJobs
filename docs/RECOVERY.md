# Recovery and rollback

## Core unavailable
PlexonJobs has a hard dependency on PlexonCore and fails enable. It does not register an independent high-frequency fallback listener.

## Vault unavailable
Job activity can still be evaluated. In PRIMARY mode the module reports a degraded economy state and pending money remains in the live-process ledger. Coalesced payout flushes refresh Vault provider discovery before deciding availability, so a provider that disappears and later returns can recover without restarting PlexonJobs. `/jobsadmin payout retry` clears blocked retry state; the next flush performs provider discovery again.

`payout.retry-limit` is the number of retry attempts after the initial failed deposit. Vault exposes no plugin-supplied idempotency key, so PlexonJobs does not claim crash-safe cross-process exactly-once payout semantics.

## Invalid configuration
Reload is fail-closed. `config.yml`, `jobs.yml` and `messages.yml` are strictly parsed before the accepted runtime is replaced. Type/range/Core compatibility validation completes before the live registry/subscription is swapped. Any failure leaves the previous known-good runtime active.

Obsolete job-ID reconciliation occurs only after a new runtime becomes authoritative. A failed reload therefore cannot prune profile data according to an unaccepted definition set.

## Profile load/persistence failure
Profile loads fail closed; rewards are not accepted before a profile is READY. A transient load failure enters FAILED state and is retried after a bounded backoff instead of remaining permanently failed until server restart.

`player_jobs` is persisted as an exact transactional snapshot: existing rows for the player are removed inside the save transaction and replaced by the current snapshot. When an accepted job registry no longer contains a persisted job ID, loaded profiles prune that obsolete ID, advance their revision and become dirty so the stale SQLite row is removed on persistence.

Asynchronous dirty saves are tracked. Graceful shutdown stops producers, waits for already-running profile writes, then writes final authoritative snapshots so an older save cannot overwrite newer XP/level state afterward.

## Daily-cap persistence failure
PRIMARY rewards fail closed until the player's persisted current-day cap snapshot has been hydrated. A failed hydration is retried after bounded backoff rather than treating missing state as zero.

Daily counter mutations are memory-first and periodically persisted as **absolute** schema-2 `daily_earnings` snapshots. A failed asynchronous write leaves state dirty for retry; an older successful completion cannot clear a newer dirty revision. Graceful shutdown waits for in-flight daily writes and writes final dirty snapshots before plugin teardown.

This makes graceful same-day restart behavior persistent without putting SQL on the work-event path. It is not a synchronous transaction log: an abrupt host/process crash can lose mutations newer than the most recent completed coalesced snapshot.

## SHADOW persistence failure
A drained SHADOW batch is written as one SQLite transaction. A failed batch rolls back as a whole and is requeued as a whole, avoiding duplicate totals from partial-prefix commits. Graceful shutdown waits for already-running SHADOW writes before the final blocking flush.

## Migration
Legacy Jobs data is read-only. Always back up both the legacy source and `plugins/PlexonJobs/jobs.db` before execution. Stable 1.1.0 retains fail-closed scan/plan behavior; it does not guess a proprietary legacy schema.

## Stable rollback
The previous stable rollback for `v1.1.0` is immutable `v1.0.0`:

- tag: `v1.0.0`
- source: `24b8e61950cb3a01112351e19c733d9a80953a03`
- JAR: `PlexonJobs-1.0.0.jar`
- JAR SHA-256: `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`

Schema remains 2, but take a matching pre-deployment backup of `plugins/PlexonJobs/jobs.db`, `config.yml`, `jobs.yml`, `messages.yml` and `migration.yml`. For operational rollback, stop the server, restore the previous stable JAR and matching backup, then start and verify Core/Vault/module state. Do not reverse historical Vault balances automatically.

Historical `v1.0.0-rc.2` and `v1.1.0-rc.1` artifacts are provenance only; neither is the preferred operational rollback target.

## GitHub closure vs live deployment
Stable `v1.1.0` is published only after the GitHub source/CI gate succeeds on exact merged `main` and the release workflow independently rebuilds and verifies its public artifacts. Release provenance records `release_gate=GITHUB_SOURCE_CI_CERTIFIED`.

Separate live PlexonCraft host certification is operational follow-up. Unless it has actually been performed, provenance records `runtime_certification=NOT_EXECUTED`; this does not invalidate the requested GitHub source/CI stable gate.
