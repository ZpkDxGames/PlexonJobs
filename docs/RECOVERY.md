# Recovery and rollback

## Core unavailable
PlexonJobs has a hard dependency on PlexonCore and fails enable. It does not register an independent high-frequency fallback listener.

## Vault unavailable
Job activity can still be evaluated. In PRIMARY mode the module reports a degraded economy state and pending money remains visible while the process is alive. Vault exposes no plugin-supplied idempotency key, so PlexonJobs does not claim crash-safe cross-process exactly-once payout semantics.

## Invalid configuration
Reload is fail-closed. `config.yml`, `jobs.yml` and `messages.yml` are strictly parsed before the accepted runtime is replaced. Type/range/Core compatibility validation then completes before the live registry/subscription is swapped. Any failure leaves the previous known-good runtime active.

## Profile persistence failure
Profile loads fail closed. No activity payout is accepted before a profile is READY. Asynchronous dirty saves are tracked. Graceful shutdown stops producers, waits for already-running profile writes, then writes final authoritative snapshots so an older save cannot overwrite newer XP/level state afterward.

## Daily-cap persistence failure
PRIMARY rewards fail closed until the player's persisted current-day cap snapshot has been hydrated. A failed hydration is retried after a bounded backoff rather than treating the missing state as zero.

Daily counter mutations are memory-first and periodically persisted as **absolute** schema-2 `daily_earnings` snapshots. A failed asynchronous write leaves the state dirty for retry; an older successful completion cannot clear a newer dirty revision. Graceful shutdown waits for in-flight daily writes and writes final dirty snapshots before plugin teardown.

This makes normal/graceful same-day restart behavior persistent without putting SQL on the work-event path. It is not a synchronous transaction log: an abrupt host/process crash can lose mutations newer than the most recent completed coalesced snapshot. Preserve that limitation in operational risk assessments.

## SHADOW persistence failure
A drained SHADOW batch is written as one SQLite transaction. A failed batch is rolled back as a whole and requeued as a whole, avoiding duplicate totals from partial-prefix commits. Graceful shutdown waits for already-running SHADOW writes before the final blocking flush.

## Migration
Legacy Jobs data is read-only. Always back up both the legacy source and `plugins/PlexonJobs/jobs.db` before execution. The 1.1 candidate retains the fail-closed scan/plan behavior; it does not guess a proprietary legacy schema.

## Revamp rollback
The authoritative rollback for `1.1.0-rc.1` is immutable stable `v1.0.0`:

- tag: `v1.0.0`
- source: `24b8e61950cb3a01112351e19c733d9a80953a03`
- JAR: `PlexonJobs-1.0.0.jar`
- JAR SHA-256: `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`

Schema remains 2 in the revamp, but still take a matching pre-deployment backup of `plugins/PlexonJobs/jobs.db`, `config.yml`, `jobs.yml`, `messages.yml` and `migration.yml`. For operational rollback, stop the server, restore the stable JAR and matching backup, then start and verify Core/Vault/module state. Do not reverse historical Vault balances automatically.

The older `v1.0.0-rc.2` artifact is historical provenance for the original 1.0 campaign; it is no longer the preferred rollback target now that stable `v1.0.0` exists.

## GitHub closure vs live deployment
GitHub CI proves source ancestry, tests and distribution integrity. It does not claim the PlexonCraft host has already been upgraded or certified. Candidate provenance records `runtime_certification=NOT_EXECUTED` until live deployment validation is performed.
