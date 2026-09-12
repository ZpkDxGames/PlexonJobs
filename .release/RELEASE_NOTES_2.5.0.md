# PlexonJobs 2.5.0

PlexonJobs 2.5.0 is a stable performance-architecture and premium jobs-UI release.

## Performance

- Compiles job/activity routing into immutable masks at startup/reload.
- Rejects irrelevant BREAK work by `routeMask & joinedJobMask` before player lookup or expensive reward work.
- Splits native activity handling into dynamically registered listener families.
- Dynamically narrows the PlexonCore block subscription to materials required by active online break-job members.
- Moves profile/daily hydration out of gameplay callbacks.
- Runs Explorer sampling only while an Explorer is online and only over Explorer participants.
- Defaults Hunter origin tracking to memory-only, avoiding per-spawn PDC writes.
- Separates reward feedback accumulation from one bounded global BossBar render flush.
- Demand-gates public activity reward events when no integration listens.

## Jobs UI

- New 45-slot `/jobs` dashboard.
- 54-slot paginated/filterable Job Browser.
- 45-slot Player Profile.
- 45-slot Job Details with seven-segment progress and daily context.
- Paper Dialog confirmation before a leave that resets progression.
- Custom holder/action identity and centralized click/drag safety throughout.

## Compatibility and safety

All 12 default jobs remain. Natural block provenance/player-placed rejection, SHADOW mode, daily caps, exact profile persistence, obsolete-job reconciliation, stale-write protection, graceful shutdown barriers, coalesced Vault payout, provider recovery, Builder repeat suppression, Brewer attribution, Blacksmith anti-farm protections, public API/events and PlaceholderAPI remain part of the stable contract.

No gameplay callback performs per-event SQL, Vault deposit, YAML parsing or async-task creation.

## Platform

- Java 25 / class major 69
- Paper `26.2.build.121-stable`
- PlexonCore 2.0.4
- Stable-only publication; no RC/prerelease tag

## Rollback

`v2.0.0` at `985244c61a3c07c70fb48b97ccb2883fb55149b5`, JAR SHA-256 `686710eed31a6c10e9d78cb7fccc7fdc355330a371a098ba3731940ef048ad0a`.

GitHub source/CI certification is the stable release gate. Live PlexonCraft runtime certification is separate; unless factual live evidence is supplied, release provenance records `runtime_certification=NOT_EXECUTED`.
