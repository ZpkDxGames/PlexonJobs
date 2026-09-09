# PlexonJobs 1.0.0 Release Candidate

This build is **candidate-ready**, not the stable `v1.0.0` release.

## Included

- PlexonCore API 2.x module registration and shared block-break routing.
- Core-native Miner, Woodcutter, and Digger break activities using authoritative natural/player-placed provenance.
- Twelve built-in job definitions; activity families not yet exposed by the shared Core runtime remain disabled rather than registering duplicate Bukkit listeners.
- Exact fixed-point money units and coalesced Vault payouts with pending/in-flight state and bounded retry gates.
- Player membership, total job XP, derived levels, daily caps, SQLite/WAL persistence, public API/events, PlaceholderAPI, GUI browser, diagnostics, SHADOW mode, and migration scan/plan tooling.
- Reproducible Java 25 / Paper 26.2 build and distribution verification.

## Stable-release blockers

The specification requires real server evidence before `v1.0.0`: legacy Jobs migration dry-run/execute on a staging backup, SHADOW comparison, PRIMARY staging, economy-rate review, Spark comparison, multi-player load testing, 30-minute soak testing, and rollback validation. Those results cannot be fabricated by CI.

The candidate defaults to `SHADOW` and does not make Vault deposits or mutate live job XP from activities until an administrator deliberately selects `PRIMARY` after validation.
