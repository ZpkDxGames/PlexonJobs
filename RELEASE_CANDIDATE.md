# PlexonJobs 1.0.0 RC2

RC2 supersedes RC1 after the Phase 2 source audit found release-blocking runtime-contract gaps. This remains a prerelease and is not the stable `v1.0.0` publication.

## RC2 hardening

- Removes the immediate/per-work-event Vault flush path; payouts are coalesced only.
- Pins build provenance to PlexonCore 2.0.4 and uses owner-aware Core module state/teardown semantics.
- Adds strict configuration typing/ranges and rejects unsupported payout modes.
- Adds SQLite schema version 2 with future-schema fail-closed behavior.
- Fixes persisted SHADOW `event_count` aggregation.
- Adds generation-bound profile loads and one in-flight asynchronous save per player.
- Makes PlaceholderAPI lookups cache/index based, including constant-time total daily earnings.
- Adds bounded non-granting simulation, richer diagnostics, 100,000-action deterministic load coverage and source-level performance contracts.
- Release evidence now includes SHA256SUMS, TEST_SUMMARY and PROVENANCE.

## Preserved product boundary

Miner, Woodcutter and Digger use the shared Core block-break/provenance gateway. Job families lacking authoritative shared event contexts remain disabled instead of registering duplicate high-frequency Bukkit listeners. TheosisEconomy remains authoritative through Vault.

## Stable blockers

Stable `v1.0.0` requires real PlexonCraft runtime certification: actual previous-state migration, Jobs Reborn import rehearsal where supported, Core/Vault/Theosis startup and failure recovery, player flows, provenance/anti-exploit cases, reload/restart behavior, cross-plugin interoperability, representative Spark profiling, and a >=30-minute soak. None of those runtime results are inferred from CI.
