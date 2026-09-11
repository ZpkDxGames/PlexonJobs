package com.plexon.jobs.runtime;

import java.util.concurrent.atomic.LongAdder;

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

    public void callback() { callbacks.increment(); }
    public void fastReject() { fastRejects.increment(); }
    public void originReject() { originRejects.increment(); }
    public void eligible() { eligible.increment(); }
    public void calculated() { calculated.increment(); }
    public void capped() { capped.increment(); }
    public void failedDeposit() { failedDeposits.increment(); }
    public void payoutFlush(int commits) { payoutFlushes.increment(); payoutCommits.add(Math.max(0, commits)); }
    public void shadow(long moneyMinor, long xp) { shadowMoney.add(moneyMinor); shadowXp.add(xp); }

    public Snapshot snapshot() {
        return new Snapshot(callbacks.sum(), fastRejects.sum(), originRejects.sum(), eligible.sum(),
                calculated.sum(), capped.sum(), failedDeposits.sum(), payoutFlushes.sum(), payoutCommits.sum(),
                shadowMoney.sum(), shadowXp.sum());
    }

    public record Snapshot(long callbacks, long fastRejects, long originRejects, long eligible,
                           long calculated, long capped, long failedDeposits, long payoutFlushes,
                           long payoutCommits, long shadowMoneyMinor, long shadowXp) {}
}
