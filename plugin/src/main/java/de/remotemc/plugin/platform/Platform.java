package de.remotemc.plugin.platform;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Server software detection and APIs that differ between Spigot, Paper and Folia or between
 * Minecraft versions. Paper-only calls live in {@link PaperSupport}, which is never loaded on Spigot.
 */
public final class Platform {

    public static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    public static final boolean PAPER = classExists("com.destroystokyo.paper.PaperConfig")
            || classExists("io.papermc.paper.configuration.Configuration");

    private Platform() {}

    public static String name() {
        if (FOLIA) {
            return "Folia";
        }
        return PAPER ? "Paper" : "Spigot";
    }

    /** TPS for 1, 5 and 15 minutes, or empty if the server does not expose it. */
    public static double[] tps() {
        // Folia ticks each region separately; there is no meaningful server-wide TPS.
        if (PAPER && !FOLIA) {
            try {
                return PaperSupport.tps();
            } catch (Throwable ignored) {
                // Folia: not available for the whole server
            }
        }
        return new double[0];
    }

    /** Average tick time in ms, or -1 if unknown. */
    public static double mspt() {
        if (PAPER && !FOLIA) {
            try {
                return PaperSupport.mspt();
            } catch (Throwable ignored) {
                // not available
            }
        }
        return -1;
    }

    public static String minecraftVersion() {
        if (PAPER) {
            return PaperSupport.minecraftVersion();
        }
        String v = Bukkit.getBukkitVersion(); // e.g. 1.21.4-R0.1-SNAPSHOT
        int dash = v.indexOf('-');
        return dash > 0 ? v.substring(0, dash) : v;
    }

    /**
     * A sender that runs commands with console permissions and captures their chat output.
     * On Spigot this is the console itself; output then only appears in the console stream.
     */
    public static CommandSender commandSender(List<String> output) {
        if (PAPER) {
            return PaperSupport.capturingSender(output);
        }
        return Bukkit.getConsoleSender();
    }

    public static boolean capturesCommandOutput() {
        return PAPER;
    }

    /** Entity count of a world (Paper has a cheap counter; Spigot has to list them). */
    public static int entityCount(org.bukkit.World world) {
        return PAPER ? PaperSupport.entityCount(world) : world.getEntities().size();
    }

    /** The server's command map (public on Paper, via CraftServer's method on Spigot). */
    public static org.bukkit.command.CommandMap commandMap() {
        try {
            return (org.bukkit.command.CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Command map not available", e);
        }
    }

    @SuppressWarnings("deprecation")
    public static String motd() {
        return org.bukkit.ChatColor.stripColor(Bukkit.getMotd());
    }

    /** Max health attribute; its key was renamed in 1.21.3. */
    public static double maxHealth(Player player) {
        for (String key : new String[]{"max_health", "generic.max_health"}) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute != null) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    return instance.getValue();
                }
            }
        }
        return 20.0;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
