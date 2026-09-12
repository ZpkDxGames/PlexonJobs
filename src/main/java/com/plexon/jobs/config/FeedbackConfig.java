package com.plexon.jobs.config;

public record FeedbackConfig(
        boolean enabled,
        boolean bossBarEnabled,
        int bossBarDurationTicks,
        boolean rewardSoundEnabled,
        String rewardSound,
        float rewardVolume,
        float rewardPitch,
        boolean levelTitleEnabled,
        boolean levelSoundEnabled,
        String levelSound,
        float levelVolume,
        float levelPitch
) {}
