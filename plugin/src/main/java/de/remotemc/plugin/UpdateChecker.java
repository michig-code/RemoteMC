package de.remotemc.plugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Looks up the latest GitHub release once at startup (off the main thread). */
final class UpdateChecker {

    static final String RELEASES_API = "https://api.github.com/repos/michig-code/RemoteMC/releases/latest";

    record Update(String version, String url) {}

    private UpdateChecker() {}

    /** Returns the newer release, or null if up to date / unreachable. */
    static Update check(String currentVersion) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL) // survives repository renames
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "RemoteMC/" + currentVersion)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            String latest = release.get("tag_name").getAsString().replaceFirst("^v", "");
            return isNewer(latest, currentVersion) ? new Update(latest, release.get("html_url").getAsString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Compares dotted numeric versions; pre-release suffixes are ignored. */
    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[^0-9]+");
        String[] b = current.split("[^0-9]+");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }
}
