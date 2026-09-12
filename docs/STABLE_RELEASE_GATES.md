# PlexonJobs 2.0.0 stable release gates

PlexonJobs 2.0.0 uses a GitHub source/CI certification gate for stable publication. Live PlexonCraft deployment validation remains a separate operational follow-up and never rewrites an immutable stable tag.

## GitHub stable-release gate

Stable `v2.0.0` may be published only when all of these are true on the exact final merged `main` commit:

- previous stable source `72f9225c2d337422d617ff2b5638363eeb98a3cf` (`v1.1.0`) is an ancestor;
- project version is exactly `2.0.0` with no prerelease suffix;
- Java 25 / Paper 26.2 / verified PlexonCore 2.0.4 build contract passes;
- JUnit suite is non-empty with zero failures, errors or skips;
- Javadoc, `check`, `shadowJar` and distribution verification pass;
- installable JAR is Java class major 69, contains bundled SQLite, and does not shade PlexonCore, Paper/Bukkit, Adventure, Vault or PlaceholderAPI;
- all 12 built-in jobs are enabled in the default catalog;
- Miner/Woodcutter/Digger retain PlexonCore natural-origin block-break authority;
- non-Core jobs route through the centralized `ActivityGrantService` and native adapters perform no SQL, Vault deposit, YAML parsing or task creation;
- Hunter unknown/disallowed spawn origins fail closed;
- Builder repeat-position suppression is bounded;
- Brewer attribution is bounded and fails closed when unattributed;
- Explorer uses periodic discovery sampling with no movement-event hot path;
- reward BossBar feedback is coalesced to reusable per-player state with global cleanup rather than task-per-reward behavior;
- profile-load retry/backoff, exact profile snapshots, accepted-runtime obsolete-job reconciliation, daily-cap persistence/revision guards, Vault recovery, SHADOW atomicity and shutdown write barriers remain covered;
- holder-based GUI identity/click-drag routing remains intact;
- malformed YAML/reload remains fail-closed;
- no RC publisher exists in the source tree;
- `.release/RELEASE_NOTES_2.0.0.md` exists;
- `v2.0.0` does not already exist;
- `release/stable` points exactly to final current `main`.

The Release workflow must rebuild/test that exact source, emit JAR/checksum/test/provenance evidence, create normal latest release `v2.0.0`, query the remote GitHub tag to prove it targets the exact source SHA, re-download all four public assets, and verify checksums/evidence before succeeding.

Stable provenance records `release_gate=GITHUB_SOURCE_CI_CERTIFIED` and `runtime_certification=NOT_EXECUTED` unless separate live-host certification has actually been performed.

## Previous stable rollback

- tag: `v1.1.0`
- source: `72f9225c2d337422d617ff2b5638363eeb98a3cf`
- JAR: `PlexonJobs-1.1.0.jar`
- SHA-256: `bbdc7027800029c7588005860befb0f2111cb73352f82aadebce48c3dd594e9f`

Schema remains version 2. Operational rollback should preserve matching backups of `jobs.db`, YAML configuration, and relevant player/world data because Explorer discoveries use player PDC.

## Optional live PlexonCraft verification

When deployed to the real host, verify at minimum:

- clean startup with Paper 26.2 / Java 25 / PlexonCore 2.0.4 / Vault-Theosis;
- `/jobs` GUI interaction/transfer safety and all 12 default jobs visible/available;
- representative successful activity for every job family;
- Hunter spawner/custom/unknown mob non-reward behavior;
- Builder same-position repeat suppression;
- automated/unattributed brewing non-reward behavior;
- Explorer one-time biome discovery behavior and restart persistence;
- BossBar XP/money accumulation, progress, expiry, reward sound and level-up title/sound;
- daily-cap hydration and graceful restart continuity;
- Vault outage/recovery and payout retry behavior;
- valid/malformed reload behavior;
- profile/SHADOW persistence and PlaceholderAPI;
- representative Spark comparison and multiplayer soak.

A proven live defect should be fixed in a later maintenance release rather than moving or rewriting `v2.0.0`.
