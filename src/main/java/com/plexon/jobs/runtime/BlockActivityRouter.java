package com.plexon.jobs.runtime;

import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Typed BREAK dispatch with the player-interest gate before Bukkit player lookup. */
public final class BlockActivityRouter {
    private final CompiledJobRoutes routes;
    private final ActivityInterestIndex interest;
    private final ActivityGrantService grants;
    private final JobsMetrics metrics;

    public BlockActivityRouter(CompiledJobRoutes routes, ActivityInterestIndex interest,
                               ActivityGrantService grants, JobsMetrics metrics) {
        this.routes = Objects.requireNonNull(routes);
        this.interest = Objects.requireNonNull(interest);
        this.grants = Objects.requireNonNull(grants);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public void handle(CoreBlockBreakContext context) {
        metrics.eventSeen();
        CompiledJobRoutes.CompiledRoute route = routes.breakRoute(context.material());
        if (route.jobMask() == 0L) {
            metrics.rejectedNoGlobalInterest();
            return;
        }
        PlayerExecutionState state = interest.state(context.playerId());
        long matched = state.joinedJobMask() & route.jobMask();
        if (matched == 0L) {
            metrics.rejectedNoPlayerInterest();
            return;
        }
        if (!state.rewardReady()) {
            metrics.rejectedNotReady();
            return;
        }
        if (context.origin() != BlockOrigin.NATURAL) {
            metrics.rejectedOrigin();
            return;
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null) {
            metrics.fastReject();
            return;
        }
        grants.handleBreak(player, context.material(), route, matched, context.eventId());
    }
}
