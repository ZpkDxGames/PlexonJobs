# PlexonJobs 1.0.0 release and deployment gates

PlexonJobs separates reproducible GitHub source/release closure from live PlexonCraft deployment certification.

## GitHub stable-release gate

Stable `v1.0.0` may be published only when all of these are true on the exact final `main` commit:

- accepted RC2 source `4929145d2e594fef5714319b8f668076fc66498a` is an ancestor;
- Java 25 / Paper 26.2 / PlexonCore 2.0.4 build contract passes;
- the full JUnit suite is non-empty with zero failures, errors or skips;
- Javadoc, `shadowJar` and distribution verification pass;
- the installable JAR is Java class major 69, contains bundled SQLite, and does not shade PlexonCore, Paper/Bukkit, Adventure, Vault or PlaceholderAPI;
- profile shutdown write ordering, atomic SHADOW batch persistence, SHADOW shutdown write ordering and fail-closed YAML reload contracts are covered by source/tests;
- stable release notes exist;
- `v1.0.0` does not already exist;
- `release/stable` points to the exact current `main` SHA;
- the Release workflow rebuilds/tests that exact SHA, publishes JAR/checksum/test/provenance assets, downloads those public assets and verifies their checksum/provenance before succeeding.

The GitHub provenance file records `runtime_certification=NOT_EXECUTED`; this is an explicit statement that live-host validation is separate, not a failed source/release gate.

## Live PlexonCraft deployment checklist

The following gates apply when deploying/certifying the stable artifact on the real host.

### Installation and migration

- Back up the actual prior PlexonJobs/Jobs Reborn state.
- Start with the supported PlexonCore, Vault, TheosisEconomy and required optional integrations.
- Inspect the actual Jobs Reborn source schema; dry-run/import only if supported by explicit mapping.
- Verify unknown mappings, idempotency/completion behavior and rollback.

### Player flow

- `/jobs`, browse/info, join, leave, leave-all, stats and earnings.
- Multiple jobs, default limit, permission limit and permission reduction without deleting existing membership.
- Reconnect, restart and stale-load behavior.
- XP accrual, level transition, multiple-level crossing and max level.

### Work/provenance and anti-exploit

- Natural Miner/Woodcutter/Digger work grants exactly configured XP/money in PRIMARY.
- PLAYER_PLACED and UNKNOWN origins fail closed for natural-only mining.
- Cancelled/protected work does not reward.
- Creative/spectator and disabled-world policy.
- Repeated place/break resource farming attempts.
- No duplicate processing/reward loops across PlexonSkills, PlexonQuests, PlexonSpawners or PlexonTools.

### Payout/economy

- TheosisEconomy is the observed Vault provider.
- Fractional/repeated payout totals match fixed-unit expectations.
- Sustained work coalesces to bounded Vault deposits, never one deposit per work event.
- Provider outage keeps money pending during the live process; recovery/retry does not duplicate payout.
- Logout, graceful shutdown and restart behavior are observed.
- Preserve the explicit limitation: Vault has no plugin-supplied idempotency key, so distributed cross-process exactly-once is not claimed.

### Persistence and reload

- Graceful shutdown/restart preserves the newest job XP/level state under concurrent dirty saves.
- SHADOW aggregate totals remain exact across scheduled writes and shutdown.
- Valid reload swaps definitions without duplicate subscription/task.
- Invalid/malformed `config.yml` or `jobs.yml` retains the known-good runtime.
- PlaceholderAPI values remain cache-only.
- Core owner-aware module lifecycle is clean across disable/start.

### Performance certification

Capture representative Spark evidence for enabled work paths and mixed-player load. Record TPS, MSPT distribution where available, PlexonJobs/Core/Vault contributions, DB/persistence behavior and scheduler behavior.

Prove in runtime evidence: no Vault deposit per work event, no DB query/write per work event, no task per work event, bounded payout accumulation and bounded persistence. Run a representative soak and inspect logs/diagnostics afterward.

Live certification results may update deployment records, but must not rewrite the already-published stable tag or its provenance.
