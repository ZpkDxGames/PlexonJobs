# PlexonJobs 1.1.0 RC1

`1.1.0-rc.1` is the full product/architecture revamp candidate based on immutable stable `v1.0.0` (`24b8e61950cb3a01112351e19c733d9a80953a03`). It is a source/CI candidate only until the live PlexonCraft gates below are completed.

## Candidate boundary

The revamp deliberately preserves the Core-native work engine instead of rewriting proven hot-path architecture.

- Miner, Woodcutter and Digger remain enabled through PlexonCore's authoritative natural-origin block-break gateway.
- Unsupported job families remain unavailable until an authoritative shared context exists; no duplicate/fallback high-frequency listeners were added.
- TheosisEconomy remains authoritative through Vault.
- SQLite remains schema 2.
- Cross-process exactly-once Vault payout semantics remain explicitly not claimed.
- Jobs Reborn migration execute remains fail-closed.

## RC1 changes

- Interactive holder-based 36-slot jobs overview and 27-slot details/confirmation views.
- Explicit slot actions with one click/drag router; no title/name/lore action identity.
- Progression-reset leave confirmation in GUI and direct command paths.
- Configurable Adventure/MiniMessage message surface with old-file fallback defaults.
- Initial-enable registration of the public `PlexonJobsAPI` service.
- Dynamic PlaceholderAPI plugin version and authoritative daily-state placeholder gating.
- Graceful-restart-safe daily cap snapshots using the existing `daily_earnings` table.
- Async per-player cap hydration, coalesced absolute writes and stale-completion revision protection.
- Daily state diagnostics and join-time profile/cap prewarming.
- Removal of dead `/jobs top` placeholder behavior.
- CI source-contract checks preventing legacy null-holder/title/display-name identity and `ChatColor` regression.

## GitHub verification gate

Before this branch may be treated as an installable RC candidate, exact-head CI must prove:

- stable `v1.0.0` source is an ancestor;
- Java 25 / Paper 26.2 build succeeds;
- all tests execute with zero failure/error/skip;
- Javadocs/check/distribution verification pass;
- `PlexonJobs-1.1.0-rc.1.jar` contains the expected API, menu, persistence and SQLite runtime classes;
- runtime APIs such as Paper, PlexonCore, Adventure, Vault and PlaceholderAPI are not accidentally shaded;
- JAR/plugin/manifest versions agree;
- exact SHA-256, test summary and provenance artifacts are emitted.

## Live PlexonCraft gates before stable promotion

Source CI is not runtime certification. On the real PlexonCraft host, verify all of the following against the exact CI artifact:

1. Clean startup with Paper 26.2, Java 25, PlexonCore 2.0.4, Vault/TheosisEconomy and existing `jobs.db`/configuration.
2. `/jobs` opens the 36-slot overview and all twelve configured job families render without broken/empty state.
3. Miner/Woodcutter/Digger details, join and leave actions work; unsupported jobs remain non-granting and explain their unavailable state.
4. Click/drag regression: left/right/shift, hotbar-number, off-hand/double-click where applicable, bottom-inventory transfer attempts, close/reopen and rapid repeated clicks do not move/duplicate menu items or execute unknown actions.
5. If `keep-level-on-leave: false`, both GUI and command paths require confirmation before progression reset.
6. PRIMARY natural-origin work grants the expected XP/money while placed/unknown origin and disallowed game mode/world paths do not.
7. Daily cap state is hydrated before PRIMARY grants; `/jobs earnings`, menu earnings and PlaceholderAPI do not show false zero while hydration is pending.
8. Earn against a non-zero daily cap, perform a graceful restart, then prove the same-day counter resumes from its persisted value and cannot be re-earned from zero.
9. Verify profile, daily-cap and SHADOW persistence across restart; inspect logs for rejected/stale/failed writes.
10. Exercise `/jobsadmin reload` with valid and malformed configuration/message files; malformed candidates must leave the accepted runtime active.
11. Exercise Vault unavailable/recovery and `/jobsadmin payout retry`; verify no tight retry loop or duplicate acknowledgement in the live process.
12. Run representative Spark before/after profiling while actively breaking eligible natural blocks and using `/jobs`; compare MSPT contribution, task count and allocation hotspots.
13. Run a multi-player representative soak and confirm no retained menu/session state, runaway task growth or database queue buildup.

## Rollback

Stable rollback is immutable `v1.0.0`:

- source `24b8e61950cb3a01112351e19c733d9a80953a03`
- JAR `PlexonJobs-1.0.0.jar`
- SHA-256 `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`

Schema remains 2, but operational rollback should still take a matching pre-deployment backup of `plugins/PlexonJobs/jobs.db` and YAML files. Do not infer live PASS from GitHub CI.
