# PlexonJobs 1.1.0 stable release gates

PlexonJobs 1.1.0 uses a GitHub source/CI certification gate for stable publication. Live PlexonCraft deployment validation is a separate operational follow-up and does not change the immutable GitHub stable tag.

## GitHub stable-release gate

Stable `v1.1.0` may be published only when all of these are true on the exact final merged `main` commit:

- previous stable source `24b8e61950cb3a01112351e19c733d9a80953a03` (`v1.0.0`) is an ancestor;
- the project version is exactly `1.1.0` with no prerelease suffix;
- Java 25 / Paper 26.2 / verified PlexonCore 2.0.4 build contract passes;
- the JUnit suite is non-empty with zero failures, errors or skips;
- Javadoc, `check`, `shadowJar` and distribution verification pass;
- the installable JAR is Java class major 69, contains bundled SQLite, and does not shade PlexonCore, Paper/Bukkit, Adventure, Vault or PlaceholderAPI;
- holder-based GUI identity/click-drag routing contracts pass;
- profile-load retry/backoff is present and covered;
- `player_jobs` persistence is an exact transactional snapshot and obsolete job definitions are reconciled only after runtime acceptance;
- same-day cap hydration/persistence and stale-revision guards pass;
- Vault provider discovery is refreshable during payout flushing;
- negative public-API XP mutations are rejected and zero is a no-op;
- profile/daily/SHADOW graceful-shutdown write ordering is covered;
- malformed YAML reload remains fail-closed;
- no RC publisher remains in the stable source tree;
- `.release/RELEASE_NOTES_1.1.0.md` exists;
- `v1.1.0` does not already exist;
- `release/stable` points exactly to the final current `main` SHA.

The Release workflow must then rebuild/test that exact source, emit JAR/checksum/test/provenance evidence, create normal latest release `v1.1.0`, query the **remote GitHub tag** to prove it targets the exact source SHA, download the four public assets, and verify checksum/evidence before succeeding.

Stable provenance records `release_gate=GITHUB_SOURCE_CI_CERTIFIED`. It also records `runtime_certification=NOT_EXECUTED` unless separate live-host certification has actually been performed; this is an explicit scope statement, not a failed GitHub release gate.

## Previous stable rollback

- tag: `v1.0.0`
- source: `24b8e61950cb3a01112351e19c733d9a80953a03`
- JAR: `PlexonJobs-1.0.0.jar`
- SHA-256: `f6adfa64e60f195e9528be37c5e91e8938453a3c6635b5b2a5ba75a102cdeaa0`

Schema remains version 2. Operational rollback should still preserve a matching backup of `plugins/PlexonJobs/jobs.db` and YAML configuration.

## Optional live deployment verification

When deploying on the real PlexonCraft host, verify startup, `/jobs` GUI interaction/transfer safety, enabled natural-origin reward paths, disabled job families, daily-cap hydration and graceful restart, Vault outage/recovery, profile persistence, malformed reload recovery, PlaceholderAPI behavior, Spark contribution and a representative soak.

These operational observations may inform a later maintenance release if a real defect is found, but they must not move or rewrite the already-published `v1.1.0` tag.
