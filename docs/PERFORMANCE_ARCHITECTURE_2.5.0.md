# PlexonJobs 2.5.0 performance architecture

## Objective

2.5.0 makes rejection of irrelevant high-frequency gameplay work cheaper synchronously. It does **not** create asynchronous work per block break, kill, place, craft or other gameplay callback. SQLite and other blocking persistence remain behind the existing IO scheduler; reward routing remains main-thread-safe and memory-first.

## Listener model

Bukkit/Paper listeners are global registrations. PlexonJobs therefore does not create a listener per player. `ActivityListenerCoordinator` owns one listener instance per activity family and reconciles global registration from online participation:

- zero participants: family listener absent;
- first participant: register once;
- additional participants: no duplicate registration;
- last participant leaves: unregister with `HandlerList.unregisterAll(listener)`.

`PlayerStateListener` remains always registered because profile/daily warmup and quit cleanup are low-frequency lifecycle work.

## Compiled job routes

`CompiledJobRoutes` is immutable after candidate compilation. The accepted catalog is limited to 64 job definitions so each job has one bit in a `long`.

Compiled structures include:

- `Material -> CompiledRoute` for BREAK;
- activity + normalized key -> `CompiledRoute`;
- wildcard routes folded into each exact route at compile time;
- job ID -> bit;
- bit -> `JobDefinition`;
- per-job activity masks.

BREAK never performs `Material -> String -> Material` conversion. Exact/wildcard sets are not constructed inside gameplay callbacks.

## Per-player interest index

`ActivityInterestIndex` stores a compact immutable `PlayerExecutionState` for each online player:

```text
joinedJobMask
activityMask
profileReady
dailyReady
rewardReady
```

The index changes only on lifecycle/topology transitions: profile READY, daily READY, join/leave job, join/quit server, accepted reload and reconciliation. Gameplay callbacks read it but never rebuild it.

## BREAK path

The hot path is:

```text
CoreBlockBreakContext
-> typed material route
-> routeMask & joinedJobMask
-> readiness
-> natural-origin check
-> Bukkit player lookup
-> specialized typed grant route
-> in-memory daily/progression/payout accrual
-> feedback accumulator
-> public event only if observed
```

A player without Miner/Woodcutter/Digger membership returns before Bukkit player lookup, profile/daily hydration, generic string routing, custom events, feedback or reward math.

## Dynamic PlexonCore subscription

`CoreBlockSubscriptionCoordinator` derives the union of BREAK materials needed by online joined jobs. The subscription changes only when participation/topology changes. It never resubscribes from a block callback.

Examples:

- no online break-job members -> no PlexonJobs Core subscription;
- only Woodcutter online -> Woodcutter materials only;
- Miner joins -> union expands;
- last break-job member leaves -> subscription closes.

## Readiness and persistence

Profile and daily hydration begin from player lifecycle work. In PRIMARY mode, `rewardReady` requires both authoritative profile and current-day daily state. If either is unavailable, gameplay fails closed immediately.

This optimization does not weaken persistence contracts: exact profile snapshots, daily revisions, async SQLite boundaries, obsolete-job reconciliation, SHADOW atomicity and graceful shutdown barriers remain authoritative.

## Feedback

`ActivityGrantService` calls `RewardFeedbackSink` directly. `PlayerFeedbackService.onReward` mutates compact state only: job, XP delta, money delta, level, total XP, expiry, dirty flag and pending sound state. It does not parse MiniMessage or construct/update a BossBar.

One plugin-level task runs `flush()` every `performance.feedback-flush-ticks` (default 3). The flush renders dirty players, reuses one BossBar per player and removes expired/offline state. Rare level-up title/sound feedback can remain immediate.

## Public event demand gating

The public Bukkit API events remain available. The activity grant path checks the event `HandlerList` first; when there are no registered listeners, event allocation and dispatch are skipped. Internal feedback is therefore not an artificial subscriber.

## Hunter origin tracking

Default:

```yaml
activity:
  hunter:
    origin-tracking: MEMORY
```

Allowed spawns store only the entity UUID in a bounded in-process eligibility set. Death/remove/unload removes it. Unknown state after unload/restart fails closed. This avoids PDC writes on every observed spawn.

`PERSISTENT_PDC` is available for operators who explicitly prefer origin persistence across unload/restart and accept the extra entity-data writes.

## Explorer

Explorer uses no `PlayerMoveEvent`. Its periodic sampler is absent while no online Explorer participant exists. When active it iterates only the compiled Explorer participant collection.

## Async boundary

Gameplay listeners do not create async tasks. Async/IO remains appropriate for SQLite, serialized persistence and other truly blocking work. The gameplay route itself is intentionally made cheaper synchronously.

## Diagnostics

`/jobsadmin diagnostics` exposes:

- events seen;
- no-global-interest, no-player-interest, not-ready and origin rejects;
- route matches and committed grants;
- per-family listener active state;
- Core subscription active/material count;
- public events skipped/dispatched;
- feedback accumulations/visual flushes/dirty players.

No reward/event is logged individually.

## Profiling procedure

When live runtime access exists, compare `v2.0.0` and `v2.5.0` under the same workload with spark, including normal profiling, ticks-over-10ms profiling and allocation profiling. Record average MSPT, long ticks, PlexonJobs server-thread samples, allocation contribution and relevant route/grant/feedback/event counts.

Do not claim measured runtime improvement without an actual comparable live profiler run. GitHub certification proves architecture/source/test contracts only.
