# PlexonJobs 2.5.0 feature viability audit

This matrix records the full-source product/performance audit used for 2.5.0. Scores are qualitative and decisions describe the accepted release boundary.

| Surface | Player value | Usage | Discoverability | Hot/allocation cost | Storage/config cost | Exploit risk | Ecosystem duplication | Maintenance | Decision |
|---|---|---|---|---|---|---|---|---|---|
| Miner/Woodcutter/Digger BREAK | High | Very high | High | Previously high | Low | Medium | Core already owns provenance | Medium | REDESIGN: compiled masks + dynamic Core subscription |
| Farmer | High | High | High | Medium | Low | Medium | None | Low | REDESIGN: independent dynamic listener |
| Hunter | High | High | High | Spawn tracking previously high | Low | High | None | Medium | REDESIGN: dynamic listener + MEMORY origin default |
| Fisher | Medium | Medium | High | Low | Low | Low | None | Low | KEEP + dynamic listener |
| Builder | High | High | High | Medium | Bounded cache | High | None | Medium | KEEP safety + dynamic listener |
| Crafter/Smelter | High | Medium | High | Low | Low | Medium | None | Low | MERGE into one dynamic crafting family |
| Blacksmith | Medium | Medium | High | Low | Low | High | None | Medium | KEEP safety + dynamic listener |
| Brewer | Medium | Medium | High | Low | Bounded attribution | High | None | Medium | KEEP attribution + dynamic listener |
| Enchanter | Medium | Medium | High | Low | Low | Low | None | Low | KEEP + dynamic listener |
| Explorer | Medium | Periodic | High | Previously scans all online | Player PDC | Low | None | Medium | REDESIGN: participant-only task, absent when unused |
| Generic per-event routing | None directly | Very high | N/A | High allocation/lookup cost | None | N/A | Duplicated lookups | High | REMOVE from hot path; compile once |
| ActivityInterestIndex | High indirect | Very high reads | N/A | O(1) read | Small online memory | Low | None | Low | ADD |
| Dynamic listener coordinator | High indirect | Transition-only | N/A | Reduces idle callbacks | Small | Low | None | Medium | ADD |
| Dynamic Core subscription | High indirect | Transition-only | N/A | Reduces block gateway fanout | Small | Low | Complements Core | Medium | ADD |
| Public Bukkit events | Integration value | Reward-time | API-visible | Allocation/dispatch if unused | None | Low | External integration contract | Medium | KEEP; demand-gate allocation/dispatch |
| BossBar feedback | High | High reward rate | High | Previously renders at reward frequency | Small online state | Low | None | Medium | REDESIGN: direct accumulator + global flush |
| Dashboard | High | Frequent | Very high | Low-frequency creation | None | Low | None | Medium | REDESIGN |
| Job Browser | High | Frequent | Very high | Low-frequency creation | None | Low | None | Medium | REDESIGN + pagination/filter |
| Player Profile | High | Medium | High | Low-frequency creation | None | Low | None | Medium | ADD |
| Job Details | High | Frequent | High | Low-frequency creation | None | Low | None | Medium | REDESIGN + progress/daily context |
| Inventory confirmation | Medium | Rare | Medium | Low | None | Prevents destructive mistakes | Paper Dialog is better surface | Low | REMOVE inventory confirmation; use Dialog |
| Per-viewer GUI animations/tasks | Low | Potentially high | Cosmetic | High | None | Low | None | High | REMOVE / forbid |
| Coalesced Vault payout | High | High | Indirect | Bounded | In-memory pending | Financial | Vault authority | Medium | KEEP |
| Exact profile + daily persistence safety | High | Continuous | Indirect | Async/coalesced | SQLite | Financial/progression | None | High | KEEP |
| SHADOW mode | Admin value | Optional | Admin | Bounded | SQLite aggregate | Low | None | Medium | KEEP |
| PlaceholderAPI | Integration value | Medium | External | Memory-only | None | Low | PAPI integration | Low | KEEP |
| Legacy Jobs migration execute | Conditional | Rare | Admin | Low | External schema unknown | High | Legacy plugin | High | KEEP fail-closed; do not guess schema |

## Removed/replaced architecture

- Monolithic `NativeActivityListener` is removed.
- Generic string-key BREAK dispatch is removed from the hot path.
- Task-per-reward/per-event async patterns remain forbidden.
- Internal feedback no longer subscribes to PlexonJobs' own public reward events.
- Inventory-based destructive leave confirmation is replaced by Paper Dialog.

## Explicitly retained non-claims

- Vault cross-process exactly-once settlement is not claimed because Vault provides no plugin-supplied idempotency transaction ID.
- Daily-cap abrupt-crash exactness is not claimed because persistence is coalesced rather than synchronously journaled per reward.
