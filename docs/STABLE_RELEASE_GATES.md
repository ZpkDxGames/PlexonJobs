# PlexonJobs 2.5.0 stable release gates

PlexonJobs 2.5.0 uses an exact-source GitHub CI gate for stable publication. Live PlexonCraft deployment/profiling is separate; absent real host evidence, release provenance must state `runtime_certification=NOT_EXECUTED`.

## Stable-only boundary

- version exactly `2.5.0`;
- no RC/prerelease/snapshot/temp public candidate tag;
- no RC publisher workflow;
- branch `release/2.5.0` starts from final `main` at the 2.0.0 stable boundary;
- rollback tag/source is immutable `v2.0.0` / `985244c61a3c07c70fb48b97ccb2883fb55149b5`;
- rollback JAR SHA-256 is `686710eed31a6c10e9d78cb7fccc7fdc355330a371a098ba3731940ef048ad0a`.

## Exact branch and PR gate

The final `release/2.5.0` head must pass canonical Build with:

- compile, tests, `check`, Javadocs, shaded JAR and distribution verification;
- Java 25 / class major 69;
- Paper `26.2.build.121-stable` and verified PlexonCore 2.0.4;
- non-empty JUnit totals with zero failures/errors/skips;
- all 12 built-in jobs retained/enabled;
- `ActivityInterestIndex`, `CompiledJobRoutes`, dynamic listener coordinator and dynamic Core block subscription present;
- monolithic `NativeActivityListener` absent;
- typed BREAK route with no `Material.matchMaterial`, profile/daily hydration, SQL, Vault deposit, YAML parse or task creation in the hot path;
- no gameplay-listener task-per-event scheduling;
- direct feedback accumulator separated from one global visual flush;
- public API events retained and activity-event dispatch demand-gated;
- Explorer `PlayerMoveEvent` absent;
- Hunter MEMORY path returns before PDC mutation;
- custom `InventoryHolder`, centralized click/drag routing and Paper Dialog leave confirmation present;
- no `ChatColor`, `createInventory(null`, inventory-title identity or item display-name/lore action identity;
- SQLite bundled, while Paper/Bukkit/PlexonCore/Adventure/Vault/PAPI classes are not shaded.

After branch Build passes, freeze its exact SHA. The PR body records the frozen SHA, test totals, workflow run ID, branch JAR SHA-256, performance/GUI summary and runtime-certification status. A moved head invalidates the evidence.

PR CI must pass at that unchanged head before a normal merge commit. Squashing the accepted source lineage is forbidden.

## Final-main and stable branch gate

After merge, canonical Build must independently pass on the exact merged `main` SHA.

`release/stable` must then fast-forward, non-force, to that exact final `main`. The stable Release workflow requires `release/stable == main`, proves ancestry from `v2.0.0`, rejects an existing `v2.5.0` tag/release and rebuilds/tests the source again.

## Stable publication

The stable workflow creates normal/latest `v2.5.0`, targeted at exact final `main`, with exactly:

- `PlexonJobs-2.5.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

It then re-downloads all assets, verifies the JAR checksum and evidence equality, verifies `prerelease=false`, proves the tag target and confirms GitHub's latest release is `v2.5.0`.

## Required provenance claims

Source CI must prove and stable provenance records:

```text
player_interest_index=PASS
dynamic_activity_listeners=PASS
dynamic_core_break_subscription=PASS
typed_break_route=PASS
per_event_async_tasks=ABSENT
per_event_sql=ABSENT
per_event_vault_deposit=ABSENT
feedback_coalesced_render=PASS
custom_event_demand_gate=PASS
explorer_move_event=ABSENT
custom_inventory_holder=PASS
legacy_gui_identity_antipatterns=ABSENT
cross_process_vault_exactly_once=NOT_CLAIMED
daily_cap_crash_exactness=NOT_CLAIMED
runtime_certification=NOT_EXECUTED
```

Only the last value may differ when factual live-host evidence actually exists.

## Optional live certification

A later real-host certification should validate startup, representative activities for all 12 jobs, no-interest BREAK rejection counters, listener/subscription transitions, Hunter origin safety, Builder/Brewer safeguards, Explorer dynamic sampling, GUI flows/Dialog leave confirmation, BossBar batching, daily persistence, Vault recovery, reload rollback, PlaceholderAPI and comparable spark profiling.

Measured MSPT/allocation improvement must never be claimed from CI alone.
