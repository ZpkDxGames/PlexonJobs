package com.plexon.jobs.runtime;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/** Low-frequency lifecycle warmup; no gameplay listener duplication or block-event fallback. */
public final class PlayerStateListener implements Listener {
    private final ProfileManager profiles;
    private final DailyLimitPersistence dailyPersistence;

    public PlayerStateListener(ProfileManager profiles, DailyLimitPersistence dailyPersistence) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.dailyPersistence = Objects.requireNonNull(dailyPersistence, "dailyPersistence");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        profiles.ensure(event.getPlayer().getUniqueId());
        dailyPersistence.ensure(event.getPlayer().getUniqueId());
    }
}
