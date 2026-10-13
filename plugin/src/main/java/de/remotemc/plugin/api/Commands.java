package de.remotemc.plugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.remotemc.plugin.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Command list and tab completion for the app's console. Call on the main / global thread. */
final class Commands {

    private static final int MAX_SUGGESTIONS = 60;

    private Commands() {}

    /** All commands the console can run, without namespaced duplicates and blocked ones. */
    static JsonObject list(Collection<String> blocked) {
        CommandMap map = Platform.commandMap();
        Collection<Command> commands = map instanceof SimpleCommandMap simple ? simple.getCommands() : List.of();
        Map<String, Command> unique = new TreeMap<>();
        for (Command c : commands) {
            String name = c.getName().toLowerCase(Locale.ROOT);
            if (name.contains(":") || isBlocked(name, blocked)) {
                continue;
            }
            unique.putIfAbsent(name, c);
        }
        JsonArray arr = new JsonArray();
        unique.forEach((name, c) -> {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                o.addProperty("description", c.getDescription());
            }
            if (c.getUsage() != null && !c.getUsage().isBlank() && !c.getUsage().equals("/" + name)) {
                o.addProperty("usage", c.getUsage().replace("<command>", name));
            }
            o.addProperty("plugin", origin(c));
            JsonArray aliases = new JsonArray();
            c.getAliases().forEach(aliases::add);
            o.add("aliases", aliases);
            arr.add(o);
        });
        JsonObject o = new JsonObject();
        o.add("commands", arr);
        return o;
    }

    /** Who brings the command: a plugin's name, or Minecraft / Bukkit / Paper / Spigot / Server. */
    static String origin(Command c) {
        if (c instanceof PluginIdentifiableCommand pc && pc.getPlugin() != null) {
            return pc.getPlugin().getName();
        }
        return origin(c.getClass().getName());
    }

    static String origin(String className) {
        if (className.endsWith("VanillaCommandWrapper")) {
            return "Minecraft";
        }
        if (className.startsWith("io.papermc.") || className.startsWith("com.destroystokyo.paper.")) {
            return "Paper";
        }
        if (className.startsWith("org.spigotmc.")) {
            return "Spigot";
        }
        if (className.startsWith("org.bukkit.")) {
            return "Bukkit";
        }
        return "Server";
    }

    /** Online player names starting with the last word of {@code buffer}. */
    static List<String> players(String buffer) {
        String word = buffer.substring(buffer.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(org.bukkit.entity.Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(word))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Suggestions for the last word of {@code buffer} (e.g. "gamemode cr" → ["creative"]). */
    static JsonObject complete(String buffer, Collection<String> blocked) {
        String label = buffer.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        List<String> suggestions;
        if (isBlocked(label.substring(label.indexOf(':') + 1), blocked) && buffer.contains(" ")) {
            suggestions = List.of();
        } else {
            List<String> raw;
            try {
                raw = Platform.commandMap().tabComplete(Bukkit.getConsoleSender(), buffer);
            } catch (RuntimeException | LinkageError e) {
                // Another plugin's tab completer failed: still help with player names, like the
                // game does for commands without their own completion.
                raw = players(buffer);
            }
            suggestions = raw == null ? List.of() : raw.stream()
                    .filter(s -> buffer.contains(" ") || (!s.contains(":") && !isBlocked(s.replace("/", ""), blocked)))
                    .distinct()
                    .limit(MAX_SUGGESTIONS)
                    .toList();
        }
        JsonArray arr = new JsonArray();
        suggestions.forEach(arr::add);
        JsonObject o = new JsonObject();
        o.add("suggestions", arr);
        return o;
    }

    private static boolean isBlocked(String label, Collection<String> blocked) {
        return blocked.stream().anyMatch(b -> b.equalsIgnoreCase(label));
    }
}
