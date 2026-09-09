# PlexonJobs

PlexonJobs is a first-party, Core-native occupation and economy progression plugin for PlexonCraft.

## Status

`1.0.0` source is implemented as a **candidate-ready** build. The plugin defaults to `SHADOW` mode because the stable release gates require live migration, economy comparison, PRIMARY staging, Spark profiling, load/soak evidence, and rollback validation before legacy Jobs may be decommissioned.

The current PlexonCore 2.x runtime exposes the shared block-break gateway. PlexonJobs therefore implements the Core-native break activity family now (Miner, Woodcutter, Digger) and ships the remaining required built-in job definitions disabled until equivalent shared Core contexts exist for placement, combat, fishing, crafting, brewing, enchanting, repairing, farming maturity and exploration.

No Bukkit fallback event engine is registered for those missing families.

## Platform

- Paper 26.2 build 121 stable
- Java 25
- PlexonCore API 2.x (`>=2.0 <3.0`)
- Build provisioned against PlexonCore 2.0.2
- Vault optional
- PlaceholderAPI optional
- SQLite persistence

## Build

CI provisions the exact Core artifact and Gradle 9.1.0.

```bash
./gradlew --no-daemon clean test check javadoc shadowJar verifyDistribution
```

Installable output:

```text
build/libs/PlexonJobs-1.0.0.jar
```

## Safety model

- Core-origin `NATURAL` required for configured break jobs.
- `PLAYER_PLACED` and `UNKNOWN` fail closed.
- Player profiles are authoritative in memory while online.
- SQLite writes are coalesced and off the primary thread.
- Money uses fixed minor units internally.
- Vault deposits are coalesced and bounded.
- Failed deposits remain pending and are never acknowledged as committed.
- `SHADOW` calculates aggregate rewards without mutating live XP or economy.
