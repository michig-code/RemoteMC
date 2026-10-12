package de.remotemc.plugin.platform;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    static CommandSender capturingSender(List<String> output) {
        return Bukkit.createCommandSender(component -> {
            synchronized (output) {
                output.add(PlainTextComponentSerializer.plainText().serialize(component));
            }
        });
    }
}
