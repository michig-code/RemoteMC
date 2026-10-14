package de.remotemc.plugin.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * The small web app (folder {@code web/} inside the plugin JAR) that the API server shows at
 * {@code https://address:port/}, for browsers and iPhones without the app. Only the files listed
 * here can be fetched – nothing else from the JAR or the disk.
 */
public final class WebFiles {
    private static final Map<String, String> TYPES = Map.of(
            "index.html", "text/html; charset=utf-8",
            "app.js", "text/javascript; charset=utf-8",
            "style.css", "text/css; charset=utf-8",
            "manifest.webmanifest", "application/manifest+json",
            "icon.png", "image/png");

    /** A file of the web app: its bytes and its content type. */
    public record File(byte[] bytes, String type) {
    }

    private WebFiles() {
    }

    /** The file for a request path ("/" is the page itself), or null if there is none. */
    public static File find(String path) {
        String name = path == null || path.isEmpty() || path.equals("/") ? "index.html" : path.substring(1);
        String type = TYPES.get(name);
        if (type == null) {
            return null;
        }
        try (InputStream in = WebFiles.class.getClassLoader().getResourceAsStream("web/" + name)) {
            return in == null ? null : new File(in.readAllBytes(), type);
        } catch (IOException e) {
            return null;
        }
    }
}
