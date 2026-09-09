package com.plexon.jobs.economy;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public final class UnavailableJobsEconomy implements JobsEconomy {
    @Override public boolean available() { return false; }
    @Override public PayoutResult deposit(UUID playerId, OfflinePlayer player, BigDecimal amount) {
        return PayoutResult.failed("Vault is not installed or no economy provider is registered");
    }
    @Override public Optional<BigDecimal> balance(OfflinePlayer player) { return Optional.empty(); }
}
