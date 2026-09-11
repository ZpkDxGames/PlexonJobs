# Recovery and rollback

## Core unavailable
PlexonJobs has a hard dependency on PlexonCore and fails enable. It does not register an independent high-frequency fallback listener.

## Vault unavailable
Job activity can still be evaluated. In PRIMARY mode the module reports a degraded economy state and pending money remains visible while the process is alive. Vault exposes no plugin-supplied idempotency key, so PlexonJobs does not claim crash-safe cross-process exactly-once payout semantics.

## Invalid configuration
Reload is fail-closed. `config.yml` and `jobs.yml` are strictly parsed before Bukkit configuration loading can mutate/compile the next runtime. Type/range/Core compatibility validation then completes before the live registry/subscription is replaced. Any failure leaves the previous known-good runtime active.

## Profile persistence failure
Profile loads fail closed. No activity payout is accepted before a profile is READY. Asynchronous dirty saves are tracked. Graceful shutdown stops producers, waits for already-running profile writes, then writes final authoritative snapshots so an older save cannot overwrite newer XP/level state afterward.

## SHADOW persistence failure
A drained SHADOW batch is written as one SQLite transaction. A failed batch is rolled back as a whole and requeued as a whole, avoiding duplicate totals from partial-prefix commits. Graceful shutdown waits for already-running SHADOW writes before the final blocking flush.

## Migration
Legacy Jobs data is read-only. Always back up both the legacy source and `plugins/PlexonJobs/jobs.db` before execution. Stable 1.0 retains the fail-closed scan/plan behavior; it does not guess a proprietary legacy schema.

## Initial stable rollback
PlexonJobs has no older stable GitHub release. The authoritative initial rollback artifact for stable `1.0.0` is the immutable final prerelease:

- tag: `v1.0.0-rc.2`
- source: `4929145d2e594fef5714319b8f668076fc66498a`
- JAR: `PlexonJobs-1.0.0-rc.2.jar`
- JAR SHA-256: `082334093b8b9e98afe31593dd4585eb0138ffae730cd1e91e1449cf658f22f6`

For an operational rollback, stop the server, restore the matching plugin JAR and the PlexonJobs DB/config backup taken before the stable deployment, then restore any legacy Jobs command aliases/placeholders if the legacy system is being re-enabled. Do not reverse historical Vault balances automatically.

## GitHub closure vs live deployment
Stable GitHub publication proves source ancestry, tests, distribution integrity and public release assets. It does not claim the PlexonCraft host has already been upgraded or certified. Release provenance records `runtime_certification=NOT_EXECUTED` until live deployment validation is performed.
