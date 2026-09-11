# PlexonJobs 1.0.0 stable release gates

Repository CI can certify source tests, deterministic fixed-unit arithmetic, Core API compilation, distribution shape and release provenance. It cannot substitute for PlexonCraft runtime evidence.

Stable `v1.0.0` remains blocked until all applicable gates below pass on the exact frozen RC with zero HIGH/CRITICAL defects.

## Installation and migration

- Back up the actual prior PlexonJobs/Jobs Reborn state.
- Start with PlexonCore 2.0.4, Vault, TheosisEconomy and required optional integrations.
- Rehearse RC1/schema migration on a copy.
- Inspect the actual Jobs Reborn source schema; dry-run/import only if supported by explicit mapping.
- Verify unknown mappings, idempotency/completion behavior and rollback.

## Player flow

- `/jobs`, browse/info, join, leave, leave-all, stats and earnings.
- Multiple jobs, default limit, permission limit and permission reduction without deleting existing membership.
- Reconnect, restart and stale-load behavior.
- XP accrual, level transition, multiple-level crossing and max level.

## Work/provenance and anti-exploit

- Natural Miner/Woodcutter/Digger work grants exactly configured XP/money in PRIMARY.
- PLAYER_PLACED and UNKNOWN origins fail closed for natural-only mining.
- Cancelled/protected work does not reward.
- Creative/spectator and disabled-world policy.
- Repeated place/break resource farming attempts.
- Natural versus PlexonSpawners entity policy once a mob-job context is activated.
- Farming maturity/replant policy once Farmer has an authoritative shared context.
- No duplicate processing/reward loops across PlexonSkills, PlexonQuests, PlexonSpawners or PlexonTools.

## Payout/economy

- TheosisEconomy is the observed Vault provider.
- Fractional/repeated payout totals match fixed-unit expectations.
- Sustained work coalesces to bounded Vault deposits, never one deposit per work event.
- Provider outage keeps money pending; recovery/retry does not duplicate payout.
- Logout with pending payout, graceful shutdown and restart behavior.
- Explicitly document any crash-boundary limitation; do not claim distributed exactly-once.

## Reload/admin/integrations

- Valid reload swaps definitions without duplicate subscription/task.
- Invalid/malformed reload retains known-good runtime.
- Admin permission denial and authorized diagnostics/simulation/retry.
- PlaceholderAPI values and cache-only behavior.
- Public API/event timing and immutable views.
- Core owner-aware module lifecycle across disable/hot-enable.

## Performance certification

Capture fresh Spark evidence under representative sustained mining, farming/mob work when those domains exist, and mixed-player load. Record TPS, median MSPT, p95/p99 where available, max MSPT, PlexonJobs listener contribution, Core contribution, Vault/Theosis contribution, DB/persistence behavior and scheduler behavior.

Prove in runtime evidence: no Vault deposit per work event, no DB write/query per work event, no task per work event, bounded payout accumulation and bounded progression persistence. Run a >=30-minute mixed soak and inspect logs/diagnostics afterward.

Only after these gates pass may the campaign consider stable `v1.0.0`. Until then the RC stays open/draft/unmerged and runtime certification is `NOT_EXECUTED`.
