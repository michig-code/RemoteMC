package de.remotemc.plugin.core;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small YAML helpers on SnakeYAML (shipped by Paper and Spigot, bundled into the Fabric mod), so
 * the shared code reads and writes the same files on every server software.
 */
public final class Yaml {

    private Yaml() {
    }

    private static org.yaml.snakeyaml.Yaml yaml() {
        DumperOptions dump = new DumperOptions();
        dump.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dump.setIndent(2);
        return new org.yaml.snakeyaml.Yaml(new SafeConstructor(new LoaderOptions()), new org.yaml.snakeyaml.representer.Representer(dump), dump);
    }

    /** The file as nested maps; empty if it is missing or invalid. */
    public static Map<String, Object> load(File file) {
        if (!file.isFile()) {
            return new LinkedHashMap<>();
        }
        try (Reader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return load(in);
        } catch (IOException | RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    public static Map<String, Object> load(InputStream in) {
        return load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(Reader in) {
        Object o = yaml().load(in);
        return o instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
    }

    /** Writes the map with "# " comment lines on top. */
    public static void save(File file, List<String> header, Map<String, Object> data) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : header) {
            text.append("# ").append(line).append('\n');
        }
        text.append(yaml().dump(data));
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    /** Value at a dotted path ("security.lockout-seconds"), or null. */
    public static Object get(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> section(Map<String, Object> root, String key) {
        Object o = root.get(key);
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }
}
