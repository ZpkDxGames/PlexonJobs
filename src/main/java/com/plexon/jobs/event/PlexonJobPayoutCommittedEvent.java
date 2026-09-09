package com.plexon.jobs.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class PlexonJobPayoutCommittedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final long moneyMinor;
    private final String provider;

    public PlexonJobPayoutCommittedEvent(UUID playerId, long moneyMinor, String provider) {
        this.playerId = Objects.requireNonNull(playerId);
        this.moneyMinor = Math.max(0, moneyMinor);
        this.provider = Objects.requireNonNullElse(provider, "unknown");
    }
    public UUID playerId() { return playerId; }
    public long moneyMinor() { return moneyMinor; }
    public String provider() { return provider; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
