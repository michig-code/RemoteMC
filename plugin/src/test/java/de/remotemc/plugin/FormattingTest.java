package de.remotemc.plugin;

import de.remotemc.plugin.live.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormattingTest {

    @Test
    void ansiBecomesSectionCodes() {
        assertEquals("§cError §rdone", Formatting.toSectionCodes("\u001B[91mError \u001B[0mdone"));
        assertEquals("§l§a!", Formatting.toSectionCodes("\u001B[1;92m!"));
        assertEquals("§x§f§f§8§0§0§0hi", Formatting.toSectionCodes("\u001B[38;2;255;128;0mhi"));
    }

    @Test
    void sectionCodesAreKept() {
        assertEquals("§aJoined", Formatting.toSectionCodes("§aJoined"));
    }

    @Test
    void stripRemovesEverything() {
        assertEquals("Error done", Formatting.strip("\u001B[91mError §r\u001B[0mdone"));
        assertEquals("plain", Formatting.strip("plain"));
    }
}
