package com.plexon.jobs.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class PlexonJobLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String jobId;

    public PlexonJobLeaveEvent(UUID playerId, String jobId) {
        this.playerId = Objects.requireNonNull(playerId);
        this.jobId = Objects.requireNonNull(jobId);
    }
    public UUID playerId() { return playerId; }
    public String jobId() { return jobId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
