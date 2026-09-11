package com.plexon.jobs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderYamlTest {
    @TempDir Path temp;

    @Test void malformedCandidateYamlFailsClosed() throws Exception {
        Path config = temp.resolve("config.yml");
        Files.writeString(config, "runtime:\n  mode: [malformed\n");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.loadStrict(config.toFile(), "config.yml"));
        assertTrue(failure.getMessage().contains("Invalid YAML in config.yml"));
        assertTrue(failure.getMessage().contains("current runtime remains unchanged"));
    }

    @Test void validCandidateYamlParsesWithoutFallback() throws Exception {
        Path config = temp.resolve("config.yml");
        Files.writeString(config, "runtime:\n  mode: PRIMARY\n");

        var yaml = ConfigLoader.loadStrict(config.toFile(), "config.yml");
        assertEquals("PRIMARY", yaml.getString("runtime.mode"));
    }
}
