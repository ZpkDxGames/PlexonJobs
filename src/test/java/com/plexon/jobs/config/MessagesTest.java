package com.plexon.jobs.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @TempDir Path temp;

    @Test
    void olderMessageFilesInheritNewEmbeddedDefaults() throws Exception {
        Path file = temp.resolve("messages.yml");
        Files.writeString(file, """
                prefix: "<gray>[Custom]</gray> "
                joined: "<green>Custom join: <job></green>"
                """);

        Messages messages = Messages.load(file.toFile());

        assertEquals("<gray>[Custom]</gray> ", messages.template("prefix"));
        assertFalse(messages.template("menu.title").isBlank(), "New menu keys must remain available to old installations");
        Component rendered = messages.render("joined", Placeholder.unparsed("job", "Miner"));
        assertNotNull(rendered);
    }

    @Test
    void nonStringMessageValuesFailClosed() throws Exception {
        Path file = temp.resolve("messages.yml");
        Files.writeString(file, """
                prefix:
                  - invalid
                """);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Messages.load(file.toFile()));
        assertTrue(failure.getMessage().contains("must be a string"));
    }
}
