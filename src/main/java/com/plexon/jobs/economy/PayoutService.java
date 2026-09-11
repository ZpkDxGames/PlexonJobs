package com.plexon.jobs.economy;

import com.plexon.jobs.event.PlexonJobPayoutCommittedEvent;
import com.plexon.jobs.runtime.JobsMetrics;
import com.plexon.jobs.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PayoutService {
    private final JavaPlugin plugin;
    private final JobsEconomy economy;
    private final PendingPayoutLedger ledger;
    private final JobsMetrics metrics;
    private final int moneyScale;
    private final int retryLimit;
    private final Map<UUID, Integer> failures = new HashMap<>();
    private final Set<UUID> blocked = new HashSet<>();

    public PayoutService(JavaPlugin plugin, JobsEconomy economy, PendingPayoutLedger ledger,
                         JobsMetrics metrics, int moneyScale, int retryLimit) {
        this.plugin = plugin;
        this.economy = economy;
        this.ledger = ledger;
        this.metrics = metrics;
        this.moneyScale = moneyScale;
        this.retryLimit = Math.max(0, retryLimit);
    }

    public void accrue(UUID playerId, long moneyMinor) {
        ledger.add(playerId, moneyMinor);
    }

    public int flush(int maxPlayers) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Vault payouts must run on the primary thread");
        if (!economy.available()) return 0;
        int limit = Math.max(1, maxPlayers);
        int committed = 0;
        for (UUID playerId : ledger.readyPlayers(limit * 2)) {
            if (committed >= limit) break;
            if (blocked.contains(playerId)) continue;
            PendingPayoutLedger.Snapshot snapshot = ledger.begin(playerId);
            if (snapshot.empty()) continue;
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            JobsEconomy.PayoutResult result;
            try {
                result = economy.deposit(playerId, offlinePlayer, Money.fromMinor(snapshot.moneyMinor(), moneyScale));
            } catch (RuntimeException failure) {
                result = JobsEconomy.PayoutResult.failed(failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            if (result.success()) {
                ledger.success(snapshot);
                failures.remove(playerId);
                blocked.remove(playerId);
                Bukkit.getPluginManager().callEvent(new PlexonJobPayoutCommittedEvent(playerId, snapshot.moneyMinor(), "Vault"));
                committed++;
            } else {
                ledger.fail(snapshot);
                metrics.failedDeposit();
                int attempts = failures.merge(playerId, 1, Integer::sum);
                if (attempts > retryLimit) {
                    blocked.add(playerId);
                    plugin.getLogger().severe("Payout retries exhausted for " + playerId + "; money remains pending. Last error: " + result.detail());
                } else {
                    plugin.getLogger().warning("Vault payout failed for " + playerId + " (attempt " + attempts + "/" + retryLimit + "): " + result.detail());
                }
            }
        }
        metrics.payoutFlush(committed);
        return committed;
    }

    public void retry(UUID playerId) {
        failures.remove(playerId);
        blocked.remove(playerId);
    }

    public void retryAll() {
        failures.clear();
        blocked.clear();
    }

    public long pending(UUID playerId) { return ledger.pending(playerId); }
    public long totalPending() { return ledger.totalPending(); }
    public int pendingPlayers() { return ledger.players(); }
    public long oldestPendingAgeMillis() { return ledger.oldestAgeMillis(); }
    public int blockedPlayers() { return blocked.size(); }
    public boolean economyAvailable() { return economy.available(); }
}
