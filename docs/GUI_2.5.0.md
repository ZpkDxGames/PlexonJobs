# PlexonJobs 2.5.0 GUI guide

## Surfaces

### Main Dashboard — 45 slots
`/jobs` opens the dashboard. It shows player/profile readiness, active jobs / maximum, total job XP, daily XP/money, pending payout/economy state and plugin help. Primary navigation is Browse Jobs, Player Profile and Active Jobs summary. Slot 40 closes the inventory.

### Job Browser — 54 slots
The browser uses content slots `10-16`, `19-25`, `28-34`, `37-43`, with Back at 45, filter at 46, Previous at 47, Profile at 49, Next at 51 and Close at 53. Pagination is implemented even when the current default catalog fits on one page.

Filters: All, Joined, Available. Job cards show enabled/joined state, level and XP-to-next when joined, authoritative daily earnings when loaded, activity-family summary and an explicit click hint.

### Player Profile — 45 slots
Shows player identity, active jobs / max, combined total XP, daily XP/money, pending payout/economy state and active-job cards. Active cards open Details. Empty profiles show `No active jobs` and a Browse Jobs action. No unpersisted lifetime-money statistic is invented.

### Job Details — 45 slots
Shows job status, level/max, total XP, XP to next, progress percentage, seven-segment progress bar, authoritative daily money/XP and configured daily caps, activity/reward summary and Join/Leave control. Back is 36, membership control is 40, Close is 44.

## Destructive leave confirmation

When `membership.keep-level-on-leave: false`, clicking Leave closes the inventory and opens a Paper Dialog confirmation. The dialog explicitly states that stored XP/level will reset. The destructive button is implemented as a local callback and validates the original viewer UUID before mutating membership on the primary thread.

The command fallback `/jobs leave <job> confirm` remains available for compatibility.

## Identity and routing safety

Every inventory is created with `JobsMenuHolder`. The holder owns:

- viewer UUID;
- page type;
- selected job where applicable;
- browser page/filter;
- slot -> typed action map.

`InventoryClickEvent` and `InventoryDragEvent` are centrally guarded. Inventory title, display name and lore never determine behavior. Player-inventory transfer paths are cancelled while a PlexonJobs top inventory is open.

## State behavior

GUI creation performs no synchronous SQLite work and no repeating viewer task. On open/navigation/join/leave it reads loaded in-memory profile, daily, payout and registry state. Missing authoritative profile/daily state renders a Loading or Error state rather than a false numeric zero.

There are no inventory animations or per-tick refreshes. Static/presentation construction is low-frequency; gameplay hot paths never build GUI items.

## Text rendering

All UI is Adventure component based. Configurable plugin messages continue through MiniMessage. Legacy `ChatColor` is forbidden.
