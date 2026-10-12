package de.remotemc.plugin.platform;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Paper-only APIs. Only referenced behind {@link Platform#PAPER} checks. */
final class PaperSupport {

    private PaperSupport() {}

    static double[] tps() {
        return Bukkit.getTPS();
    }

    static double mspt() {
        return Bukkit.getAverageTickTime();
    }

    static String minecraftVersion() {
        return Bukkit.getMinecraftVersion();
    }

    static void teleport(org.bukkit.entity.Player player, org.bukkit.Location to) {
        player.teleportAsync(to); // required on Folia, smoother on Paper
    }

    static int entityCount(org.bukkit.World world) {
        return world.getEntityCount();
    }

    /** Keeps colours as section codes; hex colours use the §x§r§r§g§g§b§b form. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    static CommandSender capturingSender(List<String> output) {
        return Bukkit.createCommandSender(component -> {
            synchronized (output) {
                output.add(LEGACY.serialize(component));
            }
        });
    }
}
