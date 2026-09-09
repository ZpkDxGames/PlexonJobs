package com.plexon.jobs.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class PlexonJobXpGainEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String jobId;
    private final long amount;
    private final long totalXp;
    private final String source;

    public PlexonJobXpGainEvent(UUID playerId, String jobId, long amount, long totalXp, String source) {
        this.playerId = Objects.requireNonNull(playerId);
        this.jobId = Objects.requireNonNull(jobId);
        this.amount = Math.max(0, amount);
        this.totalXp = Math.max(0, totalXp);
        this.source = Objects.requireNonNullElse(source, "unknown");
    }
    public UUID playerId() { return playerId; }
    public String jobId() { return jobId; }
    public long amount() { return amount; }
    public long totalXp() { return totalXp; }
    public String source() { return source; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
