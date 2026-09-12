package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityType;
import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Keeps PlexonCore as the single high-frequency block-break authority for Miner/Woodcutter/Digger. */
public final class BlockActivityRouter {
    private final JobRegistry registry;
    private final ActivityGrantService grants;

    public BlockActivityRouter(JobRegistry registry, ActivityGrantService grants) {
        this.registry = Objects.requireNonNull(registry);
        this.grants = Objects.requireNonNull(grants);
    }

    public void handle(CoreBlockBreakContext context) {
        if (registry.breakJobs(context.material()).isEmpty()) {
            grants.fastReject();
            return;
        }
        if (context.origin() != BlockOrigin.NATURAL) {
            grants.originReject();
            return;
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null) {
            grants.fastReject();
            return;
        }
        grants.handle(player, ActivityType.BREAK, context.material().name(), 1,
                "core:block:" + context.eventId());
    }
}
