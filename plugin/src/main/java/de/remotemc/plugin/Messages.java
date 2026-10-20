package de.remotemc.plugin;

import de.remotemc.plugin.core.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plugin messages from lang/&lt;language&gt;.yml, with English as fallback for missing keys.
 * Uses legacy colour codes (§) so the same texts work on Spigot, Paper and Fabric.
 */
public final class Messages {

    private final Map<String, Object> lang;
    private final Map<String, Object> fallback;

    public Messages(String language) {
        fallback = load("en");
        Map<String, Object> selected = null;
        for (String candidate : candidates(language)) {
            selected = load(candidate);
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

    private static Map<String, Object> load(String language) {
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("lang/" + language + ".yml")) {
            return in == null ? null : Yaml.load(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Message with placeholders: get("key", "name", value, "other", value2). */
    public String get(String key, Object... replacements) {
        Object value = lang.containsKey(key) ? lang.get(key) : fallback.getOrDefault(key, key);
        String raw = String.valueOf(value);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return colorize(raw);
    }

    /** "&a" → "§a" for the colour and format codes, like Bukkit's ChatColor does. */
    static String colorize(String text) {
        char[] c = text.toCharArray();
        for (int i = 0; i < c.length - 1; i++) {
            if (c[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c[i + 1]) >= 0) {
                c[i] = '§';
                c[i + 1] = Character.toLowerCase(c[i + 1]);
            }
        }
        return new String(c);
    }

    public String prefixed(String key, Object... replacements) {
        return get("prefix") + get(key, replacements);
    }
}
