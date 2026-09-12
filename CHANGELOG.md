# Changelog

## 2.5.0 — Performance architecture and premium jobs dashboard

### High-frequency routing
- Adds `CompiledJobRoutes` with typed BREAK routes, precompiled exact/wildcard activity routes and a bounded 64-job bit-mask model.
- Adds `ActivityInterestIndex` / `PlayerExecutionState` so callbacks answer player membership/readiness in O(1) without reconstructing profile membership.
- BREAK now applies the compiled route mask before Bukkit player lookup and uses a specialized typed grant entry point with no duplicate route resolution.
- Moves profile/daily hydration responsibility to player lifecycle/readiness callbacks; gameplay fails closed when state is not ready.

### Dynamic runtime topology
- Replaces the monolithic native listener with independently controlled Farmer, Hunter, Fisher, Builder, Crafter/Smelter, Blacksmith, Brewer and Enchanter listeners.
- Registers each listener family only while at least one online participant needs its activity.
- Dynamically subscribes PlexonCore only to the active union of break-job materials and closes the subscription when no online break-job participant exists.
- Explorer sampling runs only while at least one online Explorer exists and iterates the participant collection instead of all online players.
- Hunter defaults to `MEMORY` origin tracking, avoiding per-spawn PDC writes; `PERSISTENT_PDC` remains opt-in.

### Feedback and public API events
- Introduces direct internal `RewardFeedbackSink` callbacks so internal feedback does not depend on PlexonJobs public Bukkit events.
- Reward callbacks accumulate only compact dirty feedback state.
- One global bounded flush renders dirty BossBars at `performance.feedback-flush-ticks` and reuses one BossBar per player.
- Public payout/XP/reward/level events remain compatible but are allocated/dispatched by the activity pipeline only when registered listeners exist.

### Premium GUI
- `/jobs` is now a 45-slot dashboard with profile, active-job, daily earnings, pending payout/economy and help summaries.
- Adds a 54-slot paginated Job Browser with All / Joined / Available filters.
- Adds a 45-slot Player Profile with active-job cards and authoritative loading states.
- Expands Job Details to 45 slots with a seven-segment progress bar, XP/level/daily-cap information and join/leave control.
- Uses Paper Dialog API for destructive leave confirmation when leaving resets progression.
- Retains custom `InventoryHolder` identity, centralized click/drag routing, Adventure components and no title/name/lore action identity.

### Diagnostics and verification
- Adds counters for global/player/readiness/origin rejection, route matches, committed grants, listener states, Core block subscription state/material count, public-event gating and feedback accumulation/visual flushes.
- Adds deterministic source/behavior tests for compiled routing, topology, hot-path boundaries, Hunter memory mode, Explorer participant sampling, feedback batching and GUI identity.
- Stable release workflows require exact `2.5.0`, Java class major 69, all 12 default jobs, new runtime/distribution classes and zero test failures/errors/skips.

### Preserved correctness boundaries
- Preserves natural block provenance and player-placed rejection, SHADOW mode, daily caps, exact profile persistence, obsolete-job reconciliation, stale-write protection, graceful shutdown barriers, coalesced Vault payout, Vault recovery, Builder repeat suppression, Brewer attribution, Blacksmith rename-only anti-farm behavior, public API/events, PlaceholderAPI and fail-closed malformed reload.
- Cross-process exactly-once Vault payout semantics remain explicitly **not claimed**.
- Daily-cap abrupt-crash exactness remains explicitly **not claimed**.

### Stable boundary
- Target is stable `v2.5.0`; no RC/prerelease/snapshot/temp public candidate tag is used.
- Rollback is `v2.0.0`, source `985244c61a3c07c70fb48b97ccb2883fb55149b5`, JAR SHA-256 `686710eed31a6c10e9d78cb7fccc7fdc355330a371a098ba3731940ef048ad0a`.
- Live PlexonCraft runtime certification is separate and is recorded as `NOT_EXECUTED` unless real host evidence exists.

## 2.0.0 — Complete jobs and dynamic player feedback
- Enabled all 12 built-in job families and generalized exact/wildcard activity rewards.
- Preserved PlexonCore as the natural block-break authority for Miner/Woodcutter/Digger.
- Added native activity safety for Farmer, Hunter, Fisher, Builder, Crafter, Blacksmith, Brewer, Enchanter and Explorer.
- Added reusable BossBar/reward feedback, public reward event, recovery/persistence hardening and stable-only source certification.

## 1.1.0 — Stable full revamp
- Replaced display-only GUI with holder-based interactive overview/details flow.
- Added configurable MiniMessage surface, PlaceholderAPI fixes, daily-cap persistence, profile retry/exact snapshots, Vault provider recovery and shutdown barriers.

## 1.0.0 — Stable
- Introduced PlexonCore-native membership/progression/payout architecture, SHADOW/PRIMARY/DISABLED modes, public API/events and fail-closed migration tooling.
