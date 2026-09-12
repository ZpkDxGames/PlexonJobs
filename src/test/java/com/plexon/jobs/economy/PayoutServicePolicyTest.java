package com.plexon.jobs.economy;

import com.plexon.jobs.runtime.JobsMetrics;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayoutServicePolicyTest {
    @Test void refreshesEconomyProviderBeforeAvailabilityDecision() {
        RecoveringEconomy economy = new RecoveringEconomy();
        PayoutService payouts = new PayoutService(null, economy, new PendingPayoutLedger(),
                new JobsMetrics(), 2, 3);

        assertFalse(economy.available());
        assertTrue(payouts.refreshEconomyAvailability());
        assertTrue(economy.available());
        assertEquals(1, economy.refreshes);
    }

    private static final class RecoveringEconomy implements JobsEconomy {
        private boolean available;
        private int refreshes;

        @Override
        public void refresh() {
            refreshes++;
            available = true;
        }

        @Override public boolean available() { return available; }
        @Override public PayoutResult deposit(UUID playerId, OfflinePlayer player, BigDecimal amount) { return PayoutResult.ok(); }
        @Override public Optional<BigDecimal> balance(OfflinePlayer player) { return Optional.empty(); }
    }
}
