# PlexonJobs Phase 2 architecture and operations

## Ownership and dependencies

PlexonJobs owns job membership, job XP/levels, eligible-work evaluation, job payout accrual, job anti-exploit policy, job UI/admin state, persistence, API/events, PlaceholderAPI, and controlled import tooling. TheosisEconomy owns balances through Vault. PlexonSkills owns skills; PlexonQuests owns quests; PlexonSpawners/Core own their provenance domains.

Runtime: Java 25, Paper 26.2 build 121, PlexonCore 2.0.4 / API 2.x, Vault API 1.7, optional PlaceholderAPI, SQLite schema 2.

## Definitions and hot path

Job IDs are normalized stable IDs and are independent of display name, GUI slot, or file order. Definitions are parsed and compiled at startup/reload. Enabled block actions are indexed `Material -> small immutable route list`; work events never scan every job.

The current Core gateway supports authoritative block-break contexts. Miner, Woodcutter and Digger therefore use Core provenance. Non-NATURAL block origin is rejected. Other job families remain disabled until their authoritative context exists; no duplicate Bukkit fallback engine is registered.

The normal work-event path performs only cached/indexed eligibility, fixed-unit arithmetic, in-memory progression mutation, in-memory payout accrual and event publication. It performs no DB read/write, Vault deposit, scheduler submission, YAML parse, GUI render, or per-event logging.

## Progression

Total job XP is canonical. `XpCurve` precomputes a finite monotonic cumulative curve, derives levels by binary search, caps at configured max level and supports crossing multiple levels in one award. Profile state is authoritative in memory while loaded; dirty persistence is coalesced.

## Payout semantics

Money uses fixed minor `long` units. YAML decimal amounts are converted with deterministic HALF_UP rounding at the configured money scale. `payout.mode=COALESCED` is mandatory.

Each eligible PRIMARY action adds exact minor units to a per-player aggregate. A shared primary-thread flush processes a bounded number of players and performs one Vault deposit for the aggregate. A player can have only one in-flight aggregate. On provider failure the in-flight amount is restored to pending and bounded retry gates prevent tight loops. A successful commit is acknowledged once in the current process generation.

Vault has no plugin-supplied transaction-id contract. Cross-process exactly-once cannot be guaranteed. Graceful shutdown drains what it can; abrupt termination can lose memory-only unflushed accrual or create an ambiguous boundary if Vault committed immediately before process loss. Diagnostics surface pending/blocked totals. This is the deliberate minimum-complexity RC model; stable runtime certification must validate shutdown and provider-recovery behavior.

## Profiles and persistence

SQLite runs WAL/NORMAL and uses prepared statements. Schema version is stored in `migration_meta`; RC1/absent-version databases initialize to schema 2, while any future schema fails closed. Gameplay never accesses SQLite directly.

Player load runs on the Core bounded IO scheduler and is bound to a generation token plus the exact loading profile object, so a stale completion cannot overwrite a replacement state. Dirty saves permit one in-flight write per player; revision comparison prevents an older save from clearing newer dirty state. Shutdown performs a blocking final profile snapshot save.

Daily limits are authoritative in memory for the active process/day and maintain both per-job and per-player aggregate counters. Stable certification must include restart/cap behavior; the existing `daily_earnings` table is reserved for any future persisted-cap contract rather than being silently treated as implemented.

## Reload

Reload must run on the primary thread. The next YAML is read, type/range validated, Core API range checked, and job indexes/curves/runtime objects compiled before the old Core subscription is retired. A replacement Core subscription is acquired before swap; failures keep/restore the previous runtime. Money-scale changes with pending payouts and daily-limit/timezone changes are restart-only.

## Configuration validation

Known runtime fields reject incorrect scalar/list types and unsafe ranges. Runtime mode must be DISABLED, SHADOW or PRIMARY. Payout mode must be COALESCED. Job IDs must be unique normalized IDs; materials and game modes must resolve; rewards cannot be negative; money scale is bounded. A rejected reload leaves prior known-good runtime active.

## Admin, diagnostics and simulation

`/jobsadmin diagnostics` reports Core/API state, loaded definitions/routes, profile cache/loading/dirty/saving counts, economy availability, pending payout players/total/age, retry blocks, payout flush/commit/failure counts, activity/reject counts, SHADOW buffer/totals, SQLite schema, Core IO queue and gateway metrics.

`/jobsadmin simulate <job> <material> <count>` is calculation-only, grants nothing, and is bounded to 100,000 actions. `/jobsadmin payout retry` explicitly resets bounded retry gates. Migration execute is fail-closed until real source data is inspected.

## Jobs Reborn migration

The RC can scan/plan candidate source files without mutation. It intentionally does not fabricate a universal Jobs Reborn schema importer. Runtime certification must identify the actual production source format, take a backup, create an explicit job-ID mapping, dry-run, report unknown mappings, verify idempotency/completion marking, rehearse rollback, and only then add/enable execute support if technically justified.

Historical PlexonJobs RC1 data is a separate schema concern. SQLite schema 2 opens the existing RC1 tables idempotently and records the explicit schema version. A staging copy remains mandatory before production migration.

## API and PlaceholderAPI

The public service exposes immutable record views and primary-thread membership/XP mutation methods. Bukkit job events document their synchronous current implementation by being fired from authoritative primary-thread transitions. PlaceholderAPI reads only the loaded profile, registry indexes and daily-limit caches; it never queries SQLite or Vault.

## Backup and rollback

Before runtime certification: stop/flush the plugin, copy `plugins/PlexonJobs/jobs.db` plus WAL/SHM if present and all PlexonJobs YAML, and separately back up the Jobs Reborn source. RC rollback baseline is `v1.0.0-rc.1` at `e209dbc6e744882ae6cd5fd2426302f07f647010`. Do not downgrade a database whose recorded schema is newer than the target understands without restoring its matching backup.

## Release evidence

Source and PR CI must test the exact candidate. Distribution verification requires Java major 69; bundled SQLite; and no shaded PlexonCore, Paper/Bukkit, Adventure, Vault or PlaceholderAPI classes. RC publication attaches the exact-candidate JAR, SHA256SUMS, TEST_SUMMARY and PROVENANCE. PROVENANCE records candidate SHA, rollback, Java/Paper/Core/Vault/economy authority, schema and `runtime_certification=NOT_EXECUTED`.
