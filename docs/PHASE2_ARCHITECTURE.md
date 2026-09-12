# PlexonJobs 1.0 architecture and operations

> Historical stable-1.0 architecture baseline. The `1.1.0-rc.1` full revamp keeps these Core-native runtime/payout contracts but supersedes the player UI, message surface, API registration lifecycle and the process-local daily-cap limitation. See `FULL_REVAMP_1.1.0.md` and the repository README for the current candidate contract.

## Ownership and dependencies

PlexonJobs owns job membership, job XP/levels, eligible-work evaluation, job payout accrual, job anti-exploit policy, job UI/admin state, persistence, API/events, PlaceholderAPI, and controlled import tooling. TheosisEconomy owns balances through Vault. PlexonSkills owns skills; PlexonQuests owns quests; PlexonSpawners/Core own their provenance domains.

Runtime: Java 25, Paper 26.2 build 121, PlexonCore 2.0.4 / API 2.x, Vault API 1.7, optional PlaceholderAPI, SQLite schema 2.

## Definitions and hot path

Job IDs are normalized stable IDs and are independent of display name, GUI slot, or file order. Definitions are parsed and compiled at startup/reload. Enabled block actions are indexed `Material -> small immutable route list`; work events never scan every job.

The current Core gateway supports authoritative block-break contexts. Miner, Woodcutter and Digger therefore use Core provenance. Non-NATURAL block origin is rejected. Other job families remain disabled until their authoritative context exists; no duplicate Bukkit fallback engine is registered.

The normal work-event path performs only cached/indexed eligibility, fixed-unit arithmetic, in-memory progression mutation, in-memory payout accrual and event publication. It performs no DB read/write, Vault deposit, scheduler submission, YAML parse, GUI render, or per-event logging.

## Progression

Total job XP is canonical. `XpCurve` precomputes a finite monotonic cumulative curve, derives levels by binary search, caps at configured max level and supports crossing multiple levels in one award. Profile state is authoritative in memory while loaded; dirty persistence is coalesced.

Profile loads run on the Core bounded IO scheduler and are bound to both a generation token and the exact loading profile object, so stale completions cannot overwrite replacement state.

Dirty profile saves permit one in-flight write per player. The actual Core IO `CompletableFuture` is tracked. Graceful shutdown first stops work/task producers, waits for all already-scheduled profile writes to settle, and only then writes the final authoritative profile snapshots. This prevents an older asynchronous snapshot from completing after shutdown save and overwriting newer XP/level state.

## Payout semantics

Money uses fixed minor `long` units. YAML decimal amounts are converted with deterministic HALF_UP rounding at the configured money scale. `payout.mode=COALESCED` is mandatory.

Each eligible PRIMARY action adds exact minor units to a per-player aggregate. A shared primary-thread flush processes a bounded number of players and performs one Vault deposit for the aggregate. A player can have only one in-flight aggregate. On provider failure the in-flight amount is restored to pending and bounded retry gates prevent tight loops. A successful commit is acknowledged once in the current process generation.

Vault has no plugin-supplied transaction-id/idempotency contract. Cross-process exactly-once therefore cannot be guaranteed. An abrupt process crash can lose memory-only unflushed accrual or create an ambiguous boundary if Vault committed immediately before process loss. PlexonJobs explicitly records `cross_process_vault_exactly_once=NOT_CLAIMED` rather than substituting a local durable ledger that cannot make the external Vault deposit idempotent.

Graceful disable drains bounded pending payout aggregates while the provider remains available and logs any remainder.

## SHADOW persistence

SHADOW mode calculates/coalesces diagnostics without granting live XP or money. In-memory shadow totals are periodically drained for SQLite persistence.

Each drained shadow batch is persisted by `JobsDatabase.addShadowBatch(...)` as one SQLite transaction. Either every row in that drained batch commits or the transaction rolls back; on failure the whole batch is requeued. This prevents a successful prefix from being committed and then duplicated by whole-batch retry.

Scheduled shadow IO is tracked through a settled future that includes the failure/requeue handler. Graceful shutdown stops producers, waits for those tracked writes to settle, then performs one final blocking atomic shadow flush.

## Database

SQLite runs WAL/NORMAL and uses prepared statements. Schema version is stored in `migration_meta`; absent/older supported state initializes idempotently to schema 2, while any future schema fails closed. Gameplay never accesses SQLite directly.

In stable 1.0, daily limits were authoritative only in memory for the active process/day and `daily_earnings` was reserved. **This paragraph is superseded in 1.1.0-rc.1:** the existing schema-2 table now stores asynchronously hydrated/coalesced absolute same-day counter snapshots so caps survive graceful restarts without introducing SQL to the work-event hot path.

## Reload

Reload must run on the primary thread. `config.yml` and `jobs.yml` are first parsed strictly with the throwing YAML loader. Malformed YAML is rejected before Bukkit `reloadConfig()` or normal `loadConfiguration(...)` can replace the accepted configuration with fallback/default state.

After parsing, known runtime fields are type/range validated, the Core API range is checked, and job indexes/curves/runtime objects are compiled before the old Core subscription is retired. A replacement Core subscription is acquired before swap; failures keep/restore the previous runtime. Money-scale changes with pending payouts and daily-limit/timezone changes remain restart-only.

## Configuration validation

Known runtime fields reject incorrect scalar/list types and unsafe ranges. Runtime mode must be DISABLED, SHADOW or PRIMARY. Payout mode must be COALESCED. Job IDs must be unique normalized IDs; materials and game modes must resolve; rewards cannot be negative; money scale is bounded. A rejected reload leaves the prior known-good runtime active.

## Admin, diagnostics and simulation

`/jobsadmin diagnostics` reports Core/API state, loaded definitions/routes, profile cache/loading/dirty/saving counts, economy availability, pending payout players/total/age, retry blocks, payout flush/commit/failure counts, activity/reject counts, SHADOW buffer/totals, SQLite schema, Core IO queue and gateway metrics.

`/jobsadmin simulate <job> <material> <count>` is calculation-only, grants nothing, and is bounded to 100,000 actions. `/jobsadmin payout retry` explicitly resets bounded retry gates. Migration execute is fail-closed until real source data is inspected.

## Jobs Reborn migration

PlexonJobs can scan/plan candidate source files without mutation. It intentionally does not fabricate a universal Jobs Reborn schema importer. A live migration requires identifying the actual production source format, taking backups, creating an explicit job-ID mapping, dry-running, reporting unknown mappings, verifying idempotency/completion marking, rehearsing rollback, and only then enabling execute support if technically justified.

Historical PlexonJobs prerelease data is a separate schema concern. SQLite schema 2 opens supported existing tables idempotently and records the explicit schema version. A staging copy remains required before production migration.

## API and PlaceholderAPI

The public service exposes immutable record views and primary-thread membership/XP mutation methods. Bukkit job events are fired from authoritative primary-thread transitions. PlaceholderAPI reads only the loaded profile, registry indexes and daily-limit caches; it never queries SQLite or Vault.

## Initial stable rollback

The repository has no pre-1.0 stable release. The rollback artifact for the first stable publication is immutable `v1.0.0-rc.2`:

- source `4929145d2e594fef5714319b8f668076fc66498a`
- JAR `PlexonJobs-1.0.0-rc.2.jar`
- SHA-256 `082334093b8b9e98afe31593dd4585eb0138ffae730cd1e91e1449cf658f22f6`

Operational rollback must also restore the matching pre-deployment database/config backup when required. Do not downgrade a database whose recorded schema is newer than the target understands without restoring its matching backup, and do not reverse historical Vault balances automatically.

## Release evidence

Source and PR CI test the exact candidate. Distribution verification requires Java major 69; bundled SQLite; and no shaded PlexonCore, Paper/Bukkit, Adventure, Vault or PlaceholderAPI classes.

Stable publication is accepted only from `release/stable` pointing to exact current `main`. The stable Release job proves accepted RC2 ancestry, rebuilds/tests the exact SHA, publishes the JAR plus `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then downloads those public assets and verifies them.

Provenance records the exact source SHA, accepted RC2 lineage, Core dependency digest, rollback artifact, persistence/reload invariants, explicit Vault crash-boundary limitation, JAR digest/size and `runtime_certification=NOT_EXECUTED`.

Live PlexonCraft migration, functional runtime checks, Spark profiling and soak remain deployment certification. They do not rewrite or move the stable GitHub tag.
