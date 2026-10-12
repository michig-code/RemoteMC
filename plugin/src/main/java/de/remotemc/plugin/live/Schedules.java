package de.remotemc.plugin.live;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Scheduled console commands: every N minutes or daily at a time (server time zone), with
 * optional warnings in the chat before they run. Saved in schedules.json.
 */
public final class Schedules {

    public static final class Task {
        public String id;
        public String name;
        public String command;
        /** "interval" or "daily". */
        public String type;
        public int minutes;
        /** "HH:mm" for daily tasks. */
        public String time;
        /** Minutes before the run to warn players in the chat (0 = no warning). */
        public int warn;
        public boolean enabled = true;
        public long created;
        public long lastRun;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MINUTE = 60_000;

    private final File file;
    private final Logger log;
    private final ZoneId zone;
    private final List<Task> tasks = new ArrayList<>();
    /** Warnings already sent, as "id@nextRun@seconds", so each one goes out once. */
    private final Set<String> warned = new HashSet<>();

    public Schedules(File file, Logger log, ZoneId zone) {
        this.file = file;
        this.log = log;
        this.zone = zone;
        load();
    }

    /** Next run after [now] in ms; for daily tasks, missed runs while the server was off are skipped. */
    public static long nextRun(Task t, long now, ZoneId zone) {
        long since = Math.max(t.lastRun, t.created);
        if ("daily".equals(t.type)) {
            LocalTime at = LocalTime.parse(t.time);
            long after = Math.max(since, now - MINUTE);
            ZonedDateTime candidate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(after), zone).with(at).withSecond(0).withNano(0);
            while (candidate.toInstant().toEpochMilli() <= after) {
                candidate = candidate.plusDays(1);
            }
            return candidate.toInstant().toEpochMilli();
        }
        long next = since + t.minutes * MINUTE;
        return Math.max(next, now - MINUTE); // a long pause: run once soon, not many times
    }

    /** Warning points in seconds before a run: the chosen minutes, one minute and ten seconds. */
    static int[] warnPoints(int warnMinutes) {
        if (warnMinutes <= 0) {
            return new int[0];
        }
        if (warnMinutes == 1) {
            return new int[] {60, 10};
        }
        return new int[] {warnMinutes * 60, 60, 10};
    }

    /**
     * Called every few seconds: sends due warnings via [warn] (task, seconds left) and runs due
     * tasks via [run]. Returns true if something ran (the file was saved).
     */
    public synchronized boolean tick(long now, BiConsumer<Task, Integer> warn, Consumer<Task> run) {
        boolean ran = false;
        for (Task t : tasks) {
            if (!t.enabled) {
                continue;
            }
            long next = nextRun(t, now, zone);
            if (now >= next) {
                t.lastRun = now;
                run.accept(t);
                ran = true;
                continue;
            }
            for (int s : warnPoints(t.warn)) {
                long at = next - s * 1000L;
                // Only warnings that are due now (not ones already past by more than the tick).
                if (now >= at && now - at < 15_000 && warned.add(t.id + "@" + next + "@" + s)) {
                    warn.accept(t, s);
                }
            }
        }
        if (ran) {
            save();
        }
        return ran;
    }

    public synchronized JsonObject json(long now) {
        JsonArray arr = new JsonArray();
        for (Task t : tasks) {
            JsonObject o = GSON.toJsonTree(t).getAsJsonObject();
            o.addProperty("nextRun", t.enabled ? nextRun(t, now, zone) : 0);
            arr.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("schedules", arr);
        o.addProperty("timezone", zone.getId());
        o.addProperty("now", now);
        return o;
    }

    /** Adds or replaces (same id) a task; validates the fields. */
    public synchronized Task upsert(JsonObject body, long now) {
        Task t = new Task();
        t.id = body.has("id") && !body.get("id").getAsString().isBlank() ? body.get("id").getAsString() : null;
        t.name = text(body, "name", 40);
        t.command = text(body, "command", 256);
        t.type = text(body, "type", 10);
        t.warn = body.has("warn") ? Math.max(0, Math.min(30, body.get("warn").getAsInt())) : 0;
        t.enabled = !body.has("enabled") || body.get("enabled").getAsBoolean();
        switch (t.type) {
            case "interval" -> {
                t.minutes = body.has("minutes") ? body.get("minutes").getAsInt() : 0;
                if (t.minutes < 1 || t.minutes > 7 * 24 * 60) {
                    throw new IllegalArgumentException("minutes must be between 1 and 10080");
                }
            }
            case "daily" -> {
                t.time = LocalTime.parse(text(body, "time", 5)).toString(); // throws on bad input
            }
            default -> throw new IllegalArgumentException("type must be interval or daily");
        }
        Task old = t.id == null ? null : find(t.id);
        if (old == null) {
            t.id = UUID.randomUUID().toString().substring(0, 8);
            t.created = now;
        } else {
            t.created = old.created;
            // Changing when it runs starts the count again.
            boolean sameTiming = t.type.equals(old.type) && t.minutes == old.minutes && java.util.Objects.equals(t.time, old.time);
            t.lastRun = sameTiming ? old.lastRun : 0;
            if (!sameTiming) {
                t.created = now;
            }
            tasks.remove(old);
        }
        tasks.add(t);
        save();
        return t;
    }

    public synchronized boolean delete(String id) {
        boolean removed = tasks.removeIf(t -> t.id.equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized Task find(String id) {
        for (Task t : tasks) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    private static String text(JsonObject body, String key, int max) {
        if (!body.has(key) || body.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        String s = body.get(key).getAsString().replaceAll("\\p{Cntrl}", " ").strip();
        if (s.isEmpty() || s.length() > max) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return s;
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            List<Task> loaded = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), new TypeToken<List<Task>>() { }.getType());
            if (loaded != null) {
                tasks.addAll(loaded);
            }
        } catch (IOException | RuntimeException e) {
            log.warning("Could not read schedules.json: " + e.getMessage());
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), GSON.toJson(tasks), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warning("Could not save schedules.json: " + e.getMessage());
        }
    }
}
