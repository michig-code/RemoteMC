package de.remotemc.plugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plugin messages from lang/&lt;language&gt;.yml, with English as fallback for missing keys.
 * Uses legacy colour codes so the plugin also runs on Spigot (no Adventure).
 */
public final class Messages {

    private final YamlConfiguration lang;
    private final YamlConfiguration fallback;

    Messages(JavaPlugin plugin, String language) {
        fallback = load(plugin, "en");
        YamlConfiguration selected = null;
        for (String candidate : candidates(language)) {
            selected = load(plugin, candidate);
            if (selected != null) {
                break;
            }
        }
        lang = selected != null ? selected : fallback;
    }

    /** "pt_br" → pt-BR, pt; "fr-FR" → fr-FR, fr. Plain "pt" also finds the only Portuguese file. */
    static List<String> candidates(String language) {
        String[] parts = language.trim().replace('_', '-').split("-", 2);
        String base = parts[0].toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        if (parts.length > 1) {
            list.add(base + "-" + parts[1].toUpperCase(Locale.ROOT));
        }
        list.add(base);
        if (base.equals("pt") && !list.contains("pt-BR")) {
            list.add("pt-BR");
        }
        return list;
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
