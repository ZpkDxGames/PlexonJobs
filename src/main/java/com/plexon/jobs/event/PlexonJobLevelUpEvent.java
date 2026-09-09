package com.plexon.jobs.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class PlexonJobLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String jobId;
    private final int oldLevel;
    private final int newLevel;
    private final long totalXp;
    private final String source;

    public PlexonJobLevelUpEvent(UUID playerId, String jobId, int oldLevel, int newLevel, long totalXp, String source) {
        this.playerId = Objects.requireNonNull(playerId);
        this.jobId = Objects.requireNonNull(jobId);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.totalXp = totalXp;
        this.source = Objects.requireNonNullElse(source, "unknown");
    }
    public UUID playerId() { return playerId; }
    public String jobId() { return jobId; }
    public int oldLevel() { return oldLevel; }
    public int newLevel() { return newLevel; }
    public long totalXp() { return totalXp; }
    public String source() { return source; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
