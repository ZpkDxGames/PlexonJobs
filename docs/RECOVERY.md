# Recovery and rollback — PlexonJobs 2.5.0

## Core unavailable
PlexonJobs has a hard PlexonCore dependency and fails enable. It does not register an independent high-frequency fallback block listener.

## Vault unavailable
PRIMARY activity can still calculate authoritative job XP/daily state while money accrues to the live-process pending ledger. Coalesced payout flushes refresh provider discovery, so a provider may recover without restarting PlexonJobs. `/jobsadmin payout retry` clears blocked retry state.

Vault provides no plugin-supplied idempotency key; PlexonJobs therefore does not claim crash-safe cross-process exactly-once payout semantics.

## Invalid configuration / reload
Reload is fail-closed. Candidate `config.yml`, `jobs.yml` and `messages.yml` are validated before live replacement. Candidate jobs are compiled into routes/masks before install. If candidate preparation or install fails, the previous accepted runtime remains authoritative and listener/Core-subscription topology is reconciled back to it.

Daily-cap timezone/cap changes still require restart to avoid reinterpreting current-day counters. Accepted route/listener/GUI/feedback settings can reload transactionally.

Obsolete job-ID reconciliation occurs only after a candidate becomes authoritative.

## Profile / daily readiness
Profile and current-day daily state load asynchronously from lifecycle work. Gameplay never starts SQL hydration because an activity fired. PRIMARY rewards fail closed until both authoritative states are ready. Transient load failures retry after bounded backoff.

Exact profile snapshots, daily snapshot revisions, stale-completion protection and graceful-shutdown write barriers remain in place. Daily persistence remains coalesced rather than per-reward journaling; abrupt-crash exactness is not claimed.

## Dynamic listener/Core topology
`ActivityListenerCoordinator` and `CoreBlockSubscriptionCoordinator` change topology only on primary-thread participation/reload transitions. If diagnostics appear inconsistent after an operational incident, use `/jobsadmin diagnostics` to verify listener family gauges and Core subscription material count, then perform a controlled `/jobsadmin reload`. A failed reload must leave/recover the prior topology rather than accumulating duplicate listeners/subscriptions.

## Hunter
Default `MEMORY` mode writes no spawn-origin PDC. Allowed living-entity UUIDs are kept in process memory and removed on death/removal/unload. Existing/unknown entities after unload/restart fail closed.

Optional `PERSISTENT_PDC` preserves observed allowed origin across unload/restart at the cost of entity PDC writes. Unknown, spawner, trial-spawner, spawn-egg, breeding, command and plugin-custom origins remain excluded unless explicitly allowed.

## Builder / Brewer / Explorer
Builder repeat credit remains bounded TTL memory state; restart clears that throttle. Brewer attribution remains recent-interaction memory and fails closed after expiration/restart. Explorer discovery set remains player PDC, but the sampler task itself exists only while online Explorer participants exist and never uses `PlayerMoveEvent`.

Back up player/world data if Explorer discovery continuity matters.

## Feedback
Reward accumulation is non-authoritative presentation state. One reusable BossBar may exist per active player, rendered by one global flush. Disable/quit/expiry cleanup hides/removes it. Feedback failure must not alter XP, daily caps or payout state.

## SHADOW persistence
SHADOW aggregate batches remain atomic SQLite writes. Failed batches are returned to memory as a batch; graceful shutdown waits for in-flight SHADOW writes before final blocking flush.

## Migration
Legacy Jobs data stays read-only until a known source schema is deliberately supported. Back up the legacy source and `plugins/PlexonJobs/jobs.db`; do not guess proprietary legacy layouts.

## Stable rollback for 2.5.0
Authoritative rollback:

- tag: `v2.0.0`
- source: `985244c61a3c07c70fb48b97ccb2883fb55149b5`
- JAR: `PlexonJobs-2.0.0.jar`
- JAR SHA-256: `686710eed31a6c10e9d78cb7fccc7fdc355330a371a098ba3731940ef048ad0a`

Schema remains 2. Before deployment, capture matching backups of `plugins/PlexonJobs/jobs.db`, YAML configuration and relevant player/world data. To roll back, stop the server, restore the 2.0.0 JAR plus matching configuration/data backup, then start and verify Core/Vault/module state. Do not automatically reverse historical Vault balances.

## GitHub closure vs live deployment
Stable `v2.5.0` publication is an exact GitHub source/CI certification gate. Separate live PlexonCraft runtime certification and spark measurements are operational evidence. Unless they are actually executed, provenance must state `runtime_certification=NOT_EXECUTED` and no measured MSPT improvement is claimed.
