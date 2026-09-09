package com.plexon.jobs.runtime;

public enum RuntimeMode {
    DISABLED,
    SHADOW,
    PRIMARY;

    public static RuntimeMode parse(String value) {
        if (value == null) return SHADOW;
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return SHADOW; }
    }
}
