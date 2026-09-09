# Recovery and rollback

## Core unavailable
PlexonJobs has a hard dependency on PlexonCore and fails enable. It does not register an independent high-frequency fallback listener.

## Vault unavailable
Job activity can be evaluated, but live money cannot be committed. In PRIMARY mode the module reports a degraded economy state and pending money remains visible to diagnostics while the process is alive.

## Invalid configuration
Reload is atomic: validation must complete before the live registry/subscription is replaced. If validation fails, the old runtime remains active.

## Database failure
Profile loads fail closed. No activity payout is accepted before a profile is READY. Dirty profile persistence failures are logged and retained for later retry while the profile is cached.

## Migration
Legacy Jobs data is read-only. Always back up both the legacy source and `plugins/PlexonJobs/jobs.db` before execution. The candidate only scans/plans; it does not guess the legacy schema.

## Rollback
1. Set PlexonJobs to DISABLED or remove the candidate.
2. Restore the PlexonJobs DB/config backup if needed.
3. Re-enable legacy Jobs unchanged.
4. Restore command aliases/placeholders.
5. Do not reverse historical Vault balances automatically.
