package de.remotemc.plugin.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandsTest {
    @Test
    void originFromClassName() {
        assertEquals("Minecraft", Commands.origin("org.bukkit.craftbukkit.command.VanillaCommandWrapper"));
        assertEquals("Paper", Commands.origin("io.papermc.paper.command.PaperCommand"));
        assertEquals("Paper", Commands.origin("com.destroystokyo.paper.MSPTCommand"));
        assertEquals("Spigot", Commands.origin("org.spigotmc.TicksPerSecondCommand"));
        assertEquals("Bukkit", Commands.origin("org.bukkit.command.defaults.VersionCommand"));
        assertEquals("Server", Commands.origin("net.example.Something"));
    }
}
