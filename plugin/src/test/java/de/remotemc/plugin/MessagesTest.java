package de.remotemc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static final List<String> LANGUAGES = List.of("en", "de", "es", "fr", "it", "nl", "pl", "pt-BR", "tr", "ru");

    @Test
    void languageCandidates() {
        assertEquals(List.of("pt-BR", "pt"), Messages.candidates("pt_br"));
        assertEquals(List.of("pt", "pt-BR"), Messages.candidates("pt"));
        assertEquals(List.of("fr-FR", "fr"), Messages.candidates("fr-FR"));
        assertEquals(List.of("de"), Messages.candidates(" DE "));
    }

    @Test
    void everyLanguageHasAllKeysAndPlaceholders() {
        YamlConfiguration en = load("en");
        for (String language : LANGUAGES) {
            YamlConfiguration lang = load(language);
            assertEquals(en.getKeys(false), lang.getKeys(false), language);
            for (String key : en.getKeys(false)) {
                assertEquals(placeholders(en.getString(key)), placeholders(lang.getString(key)), language + " " + key);
            }
        }
    }

    private static YamlConfiguration load(String language) {
        var in = MessagesTest.class.getClassLoader().getResourceAsStream("lang/" + language + ".yml");
        assertNotNull(in, language);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = Pattern.compile("\\{[a-z]+}").matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }
}
