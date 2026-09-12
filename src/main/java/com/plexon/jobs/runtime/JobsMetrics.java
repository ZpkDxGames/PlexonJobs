package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Low-allocation counters used to prove routing topology and hot-path rejection behavior. */
public final class JobsMetrics {
    private final LongAdder callbacks = new LongAdder();
    private final LongAdder fastRejects = new LongAdder();
    private final LongAdder originRejects = new LongAdder();
    private final LongAdder eligible = new LongAdder();
    private final LongAdder calculated = new LongAdder();
    private final LongAdder capped = new LongAdder();
    private final LongAdder failedDeposits = new LongAdder();
    private final LongAdder payoutFlushes = new LongAdder();
    private final LongAdder payoutCommits = new LongAdder();
    private final LongAdder shadowMoney = new LongAdder();
    private final LongAdder shadowXp = new LongAdder();
    private final LongAdder eventsSeen = new LongAdder();
    private final LongAdder rejectedNoGlobalInterest = new LongAdder();
    private final LongAdder rejectedNoPlayerInterest = new LongAdder();
    private final LongAdder rejectedNotReady = new LongAdder();
    private final LongAdder routeMatches = new LongAdder();
    private final LongAdder grantsCommitted = new LongAdder();
    private final LongAdder customEventsSkippedNoListeners = new LongAdder();
    private final LongAdder customEventsDispatched = new LongAdder();
    private final LongAdder feedbackAccumulations = new LongAdder();
    private final LongAdder feedbackVisualFlushes = new LongAdder();
    private final AtomicInteger feedbackDirtyPlayers = new AtomicInteger();
    private final AtomicInteger blockSubscriptionActive = new AtomicInteger();
    private final AtomicInteger blockSubscriptionMaterialCount = new AtomicInteger();
    private final EnumMap<ActivityType, AtomicInteger> listenerActive = new EnumMap<>(ActivityType.class);

    public JobsMetrics() {
        for (ActivityType type : ActivityType.values()) listenerActive.put(type, new AtomicInteger());
    }

    public void callback() { callbacks.increment(); }
    public void fastReject() { fastRejects.increment(); }
    public void originReject() { originRejects.increment(); }
    public void eligible() { eligible.increment(); }
    public void calculated() { calculated.increment(); }
    public void capped() { capped.increment(); }
    public void failedDeposit() { failedDeposits.increment(); }
    public void payoutFlush(int commits) { payoutFlushes.increment(); payoutCommits.add(Math.max(0, commits)); }
    public void shadow(long moneyMinor, long xp) { shadowMoney.add(moneyMinor); shadowXp.add(xp); }

    public void eventSeen() { eventsSeen.increment(); callback(); }
    public void rejectedNoGlobalInterest() { rejectedNoGlobalInterest.increment(); fastReject(); }
    public void rejectedNoPlayerInterest() { rejectedNoPlayerInterest.increment(); fastReject(); }
    public void rejectedNotReady() { rejectedNotReady.increment(); fastReject(); }
    public void rejectedOrigin() { originRejects.increment(); }
    public void routeMatch() { routeMatches.increment(); eligible(); }
    public void grantCommitted() { grantsCommitted.increment(); }
    public void customEventSkipped() { customEventsSkippedNoListeners.increment(); }
    public void customEventDispatched() { customEventsDispatched.increment(); }
    public void feedbackAccumulation() { feedbackAccumulations.increment(); }
    public void feedbackVisualFlush() { feedbackVisualFlushes.increment(); }
    public void feedbackDirtyPlayers(int value) { feedbackDirtyPlayers.set(Math.max(0, value)); }
    public void listenerActive(ActivityType type, boolean active) { listenerActive.get(type).set(active ? 1 : 0); }
    public void blockSubscription(boolean active, int materials) {
        blockSubscriptionActive.set(active ? 1 : 0);
        blockSubscriptionMaterialCount.set(Math.max(0, materials));
    }

    public Snapshot snapshot() {
        EnumMap<ActivityType, Integer> listeners = new EnumMap<>(ActivityType.class);
        listenerActive.forEach((type, value) -> listeners.put(type, value.get()));
        return new Snapshot(callbacks.sum(), fastRejects.sum(), originRejects.sum(), eligible.sum(),
                calculated.sum(), capped.sum(), failedDeposits.sum(), payoutFlushes.sum(), payoutCommits.sum(),
                shadowMoney.sum(), shadowXp.sum(), eventsSeen.sum(), rejectedNoGlobalInterest.sum(),
                rejectedNoPlayerInterest.sum(), rejectedNotReady.sum(), routeMatches.sum(), grantsCommitted.sum(),
                customEventsSkippedNoListeners.sum(), customEventsDispatched.sum(), feedbackAccumulations.sum(),
                feedbackVisualFlushes.sum(), feedbackDirtyPlayers.get(), blockSubscriptionActive.get(),
                blockSubscriptionMaterialCount.get(), Map.copyOf(listeners));
    }

    public record Snapshot(long callbacks, long fastRejects, long originRejects, long eligible,
                           long calculated, long capped, long failedDeposits, long payoutFlushes,
                           long payoutCommits, long shadowMoneyMinor, long shadowXp,
                           long eventsSeen, long rejectedNoGlobalInterest, long rejectedNoPlayerInterest,
                           long rejectedNotReady, long routeMatches, long grantsCommitted,
                           long customEventsSkippedNoListeners, long customEventsDispatched,
                           long feedbackAccumulations, long feedbackVisualFlushes, int feedbackDirtyPlayers,
                           int blockSubscriptionActive, int blockSubscriptionMaterialCount,
                           Map<ActivityType, Integer> listenerActive) { }
}
