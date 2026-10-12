package de.remotemc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import de.remotemc.plugin.live.Schedules;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchedulesTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final long MIN = 60_000;

    private static long at(String iso) {
        return ZonedDateTime.parse(iso + "+02:00[Europe/Berlin]").toInstant().toEpochMilli();
    }

    private static JsonObject task(String type, String time, int minutes, int warn) {
        JsonObject o = new JsonObject();
        o.addProperty("name", "Restart");
        o.addProperty("command", "say hi");
        o.addProperty("type", type);
        if (time != null) o.addProperty("time", time);
        o.addProperty("minutes", minutes);
        o.addProperty("warn", warn);
        return o;
    }

    @Test
    void dailyRunsAtTheTimeAndSkipsMissedRuns() {
        Schedules.Task t = new Schedules.Task();
        t.type = "daily";
        t.time = "04:00";
        t.created = at("2026-10-10T12:00:00");
        assertEquals(at("2026-10-11T04:00:00"), Schedules.nextRun(t, at("2026-10-10T12:00:00"), ZONE));
        // Server was off at 04:00 on several days: no catch-up, next one is tomorrow.
        assertEquals(at("2026-10-14T04:00:00"), Schedules.nextRun(t, at("2026-10-13T09:00:00"), ZONE));
        t.lastRun = at("2026-10-14T04:00:02");
        assertEquals(at("2026-10-15T04:00:00"), Schedules.nextRun(t, at("2026-10-14T04:00:05"), ZONE));
    }

    @Test
    void intervalCountsFromLastRun() {
        Schedules.Task t = new Schedules.Task();
        t.type = "interval";
        t.minutes = 30;
        t.created = 0;
        t.lastRun = 100 * MIN;
        assertEquals(130 * MIN, Schedules.nextRun(t, 110 * MIN, ZONE));
        assertEquals(499 * MIN, Schedules.nextRun(t, 500 * MIN, ZONE)); // long pause: once, soon
    }

    @Test
    void tickWarnsThenRunsOnce(@TempDir Path dir) {
        Schedules s = new Schedules(dir.resolve("schedules.json").toFile(), Logger.getLogger("t"), ZONE);
        long start = at("2026-10-10T03:50:00");
        s.upsert(task("daily", "04:00", 0, 5), start);
        List<String> events = new ArrayList<>();
        for (long now = start; now <= at("2026-10-10T04:00:30"); now += 5_000) {
            s.tick(now, (t, sec) -> events.add("warn " + sec), t -> events.add("run"));
        }
        assertEquals(List.of("warn 300", "warn 60", "warn 10", "run"), events);

        // Saved and loaded again.
        Schedules again = new Schedules(dir.resolve("schedules.json").toFile(), Logger.getLogger("t"), ZONE);
        assertTrue(again.json(start).getAsJsonArray("schedules").size() == 1);
    }

    @Test
    void invalidInputIsRefused(@TempDir Path dir) {
        Schedules s = new Schedules(dir.resolve("schedules.json").toFile(), Logger.getLogger("t"), ZONE);
        assertThrows(IllegalArgumentException.class, () -> s.upsert(task("interval", null, 0, 0), 0));
        assertThrows(IllegalArgumentException.class, () -> s.upsert(task("weekly", null, 5, 0), 0));
        assertThrows(RuntimeException.class, () -> s.upsert(task("daily", "25:99", 0, 0), 0));
    }
}
