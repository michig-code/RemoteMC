package de.remotemc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.remotemc.plugin.live.History;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryTest {

    private static final long MIN = 60_000;
    private final Logger log = Logger.getLogger("test");

    @Test
    void bucketsAverageAndKeepPeaks(@TempDir Path dir) {
        History h = new History(dir.resolve("history.csv").toFile(), log);
        long now = 10_000 * MIN;
        for (int i = 0; i < 60; i++) {
            h.add(new History.Sample(now - (60 - i) * MIN, i < 30 ? 20 : 10, 5, 1000 + i, i == 10 ? 7 : 1));
        }
        JsonObject o = h.json(60 * MIN, 2, now);
        JsonArray points = o.getAsJsonArray("points");
        assertEquals(2, points.size());
        assertEquals(20.0, points.get(0).getAsJsonArray().get(1).getAsDouble(), 0.01);
        assertEquals(10.0, points.get(1).getAsJsonArray().get(1).getAsDouble(), 0.01);
        assertEquals(7, points.get(0).getAsJsonArray().get(4).getAsInt()); // peak players kept
    }

    @Test
    void survivesRestartAndDropsOldSamples(@TempDir Path dir) throws Exception {
        File file = dir.resolve("history.csv").toFile();
        long now = System.currentTimeMillis();
        History h = new History(file, log);
        h.add(new History.Sample(now - History.KEEP_MS - MIN, 20, 1, 100, 0)); // too old
        h.add(new History.Sample(now - MIN, 19.5, -1, 200, 3));
        Files.writeString(file.toPath(), "broken line\n", java.nio.file.StandardOpenOption.APPEND);

        History again = new History(file, log);
        assertEquals(1, again.samples().size());
        assertEquals(19.5, again.samples().get(0).tps(), 0.001);
        assertEquals(-1, again.samples().get(0).mspt(), 0.001);
        assertEquals(1, Files.readAllLines(file.toPath()).size()); // compacted
    }
}
