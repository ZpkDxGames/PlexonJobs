# Jobs Reborn replacement matrix

| Legacy feature | Used on PlexonCraft? | PlexonJobs equivalent | 1.0 status | Migration note |
|---|---|---|---|---|
| Join/leave jobs | To verify in staging | Membership service + `/jobs` | Implemented | Progress retained on leave |
| Miner | Expected | Core block-break + provenance | Implemented | Natural origin only |
| Woodcutter | Expected | Core block-break + provenance | Implemented | Artificial-derived support awaits Core semantics |
| Digger | Expected | Core block-break + provenance | Implemented | Natural origin only |
| Farmer | To verify | Shared harvest context | Blocked by Core context | No Bukkit fallback |
| Hunter | To verify | Shared combat/death context | Blocked by Core context | No Bukkit fallback |
| Fisher | To verify | Shared fishing context | Blocked by Core context | No Bukkit fallback |
| Builder | To verify | Shared block-place context | Blocked by Core context | No Bukkit fallback |
| Crafter | To verify | Shared craft-result context | Blocked by Core context | No Bukkit fallback |
| Blacksmith | To verify | PlexonBlacksmith domain events | Pending staging/API verification | No click inference |
| Brewer | To verify | Shared brew completion context | Blocked by Core context | No Bukkit fallback |
| Enchanter | To verify | Shared enchant-success context | Blocked by Core context | No Bukkit fallback |
| Explorer | To verify | Shared chunk-transition context | Blocked by Core context | No PlayerMove fallback |
| Money payout | Expected | Vault provider | Implemented | Coalesced |
| Job XP/levels | Expected | Total-XP canonical state | Implemented | Precomputed power curve |
| Daily caps | To verify | Fixed-unit per-day counters | Implemented | Reset timezone configurable |
| PlaceholderAPI | To verify | `%plexonjobs_*%` | Implemented | Requires PAPI |
| Leaderboards | To verify | Cached DB query | Deferred | Candidate limitation |
| Legacy DB migration | Required before stable | Scan/plan contract | Safety shell implemented | Execute requires inspected source schema |
