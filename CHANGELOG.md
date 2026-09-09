# Changelog

## 1.0.0 candidate

- Bootstrapped Java 25 / Paper 26.2 Core-native plugin.
- Added API 2.x Core module registration and shared block-break subscriptions.
- Added configuration-driven job registry with all required built-in job IDs.
- Implemented Miner, Woodcutter and Digger on Core natural-origin facts.
- Added in-memory membership, total job XP, levels and daily counters.
- Added fixed-minor-unit reward math, coalesced pending Vault ledger and bounded commits.
- Added SQLite WAL persistence with coalesced saves.
- Added public API and domain events.
- Added `/jobs`, `/jobsadmin`, GUI browsing and PlaceholderAPI integration.
- Added SHADOW/PRIMARY/DISABLED runtime modes.
- Added migration scan/plan safety shell without guessing proprietary legacy schemas.
- Added diagnostics and distribution verification.
- Documented candidate limitations and stable release gates.
