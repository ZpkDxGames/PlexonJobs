package com.plexon.jobs.economy;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface JobsEconomy {
    /** Refreshes the backing provider when the implementation supports dynamic service discovery. */
    default void refresh() { }

    boolean available();
    PayoutResult deposit(UUID playerId, OfflinePlayer player, BigDecimal amount);
    Optional<BigDecimal> balance(OfflinePlayer player);

    record PayoutResult(boolean success, String detail) {
        public static PayoutResult ok() { return new PayoutResult(true, "OK"); }
        public static PayoutResult failed(String detail) { return new PayoutResult(false, detail == null ? "unknown" : detail); }
    }
}
