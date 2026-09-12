package com.plexon.jobs.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/** Fired after PlexonJobs has applied the authoritative capped XP/money reward in PRIMARY mode. */
public final class PlexonJobRewardGrantedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String jobId;
    private final String activity;
    private final long jobXp;
    private final long moneyMinor;
    private final long totalXp;
    private final int level;
    private final long xpToNextLevel;
    private final String source;

    public PlexonJobRewardGrantedEvent(UUID playerId, String jobId, String activity,
                                       long jobXp, long moneyMinor, long totalXp,
                                       int level, long xpToNextLevel, String source) {
        this.playerId = Objects.requireNonNull(playerId);
        this.jobId = Objects.requireNonNull(jobId);
        this.activity = Objects.requireNonNullElse(activity, "unknown");
        this.jobXp = Math.max(0, jobXp);
        this.moneyMinor = Math.max(0, moneyMinor);
        this.totalXp = Math.max(0, totalXp);
        this.level = Math.max(1, level);
        this.xpToNextLevel = Math.max(0, xpToNextLevel);
        this.source = Objects.requireNonNullElse(source, "unknown");
    }

    public UUID playerId() { return playerId; }
    public String jobId() { return jobId; }
    public String activity() { return activity; }
    public long jobXp() { return jobXp; }
    public long moneyMinor() { return moneyMinor; }
    public long totalXp() { return totalXp; }
    public int level() { return level; }
    public long xpToNextLevel() { return xpToNextLevel; }
    public String source() { return source; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
