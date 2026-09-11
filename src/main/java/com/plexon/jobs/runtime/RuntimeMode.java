package com.plexon.jobs.runtime;

public enum RuntimeMode {
    DISABLED,
    SHADOW,
    PRIMARY;

    public static RuntimeMode parse(String value) {
        if (value == null || value.isBlank()) return SHADOW;
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
