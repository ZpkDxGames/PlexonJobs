package com.plexon.jobs.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class PlexonJobPayoutEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String jobId;
    private final String activity;
    private final String source;
    private long jobXp;
    private long moneyMinor;
    private boolean cancelled;

    public PlexonJobPayoutEvent(UUID playerId, String jobId, String activity, long jobXp, long moneyMinor, String source) {
        this.playerId = Objects.requireNonNull(playerId);
        this.jobId = Objects.requireNonNull(jobId);
        this.activity = Objects.requireNonNullElse(activity, "unknown");
        this.jobXp = Math.max(0, jobXp);
        this.moneyMinor = Math.max(0, moneyMinor);
        this.source = Objects.requireNonNullElse(source, "unknown");
    }
    public UUID playerId() { return playerId; }
    public String jobId() { return jobId; }
    public String activity() { return activity; }
    public long jobXp() { return jobXp; }
    public void jobXp(long value) { this.jobXp = Math.max(0, value); }
    public long moneyMinor() { return moneyMinor; }
    public void moneyMinor(long value) { this.moneyMinor = Math.max(0, value); }
    public String source() { return source; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
