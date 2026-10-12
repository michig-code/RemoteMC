package de.remotemc.plugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Plugin messages from lang/&lt;language&gt;.yml, with English as fallback for missing keys.
 * Uses legacy colour codes so the plugin also runs on Spigot (no Adventure).
 */
public final class Messages {

    private final YamlConfiguration lang;
    private final YamlConfiguration fallback;

    Messages(JavaPlugin plugin, String language) {
        fallback = load(plugin, "en");
        YamlConfiguration selected = load(plugin, language);
        lang = selected != null ? selected : fallback;
    }

    private static YamlConfiguration load(JavaPlugin plugin, String language) {
        InputStream in = plugin.getResource("lang/" + language + ".yml");
        if (in == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Message with placeholders: get("key", "name", value, "other", value2). */
    public String get(String key, Object... replacements) {
        String raw = lang.getString(key, fallback.getString(key, key));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String prefixed(String key, Object... replacements) {
        return get("prefix") + get(key, replacements);
    }
}
