package com.plexon.jobs.model;

public record ActivityReward(long jobXpUnits, long moneyMinorUnits) {
    public ActivityReward {
        if (jobXpUnits < 0) throw new IllegalArgumentException("jobXpUnits must be >= 0");
        if (moneyMinorUnits < 0) throw new IllegalArgumentException("moneyMinorUnits must be >= 0");
    }

    public static final ActivityReward ZERO = new ActivityReward(0, 0);
    public boolean empty() { return jobXpUnits == 0 && moneyMinorUnits == 0; }
}
