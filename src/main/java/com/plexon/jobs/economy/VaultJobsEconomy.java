package com.plexon.jobs.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public final class VaultJobsEconomy implements JobsEconomy {
    private volatile Economy provider;

    @Override
    public void refresh() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        provider = registration == null ? null : registration.getProvider();
    }

    @Override public boolean available() { return provider != null; }

    @Override
    public PayoutResult deposit(UUID playerId, OfflinePlayer player, BigDecimal amount) {
        Economy economy = provider;
        if (economy == null) return PayoutResult.failed("Vault economy provider unavailable");
        if (amount.signum() <= 0) return PayoutResult.ok();
        EconomyResponse response = economy.depositPlayer(player, amount.doubleValue());
        return response.transactionSuccess() ? PayoutResult.ok() : PayoutResult.failed(response.errorMessage);
    }

    @Override
    public Optional<BigDecimal> balance(OfflinePlayer player) {
        Economy economy = provider;
        return economy == null ? Optional.empty() : Optional.of(BigDecimal.valueOf(economy.getBalance(player)));
    }
}
