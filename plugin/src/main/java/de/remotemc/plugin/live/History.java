package de.remotemc.plugin.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Performance history: one sample per minute (TPS, MSPT, memory, players), kept for 7 days in
 * memory and in history.csv so it survives restarts. Served in buckets so a week fits in a chart.
 */
public final class History {

    public record Sample(long time, double tps, double mspt, long memoryMb, int players) {
        String csv() {
            return String.format(Locale.ROOT, "%d,%.2f,%.2f,%d,%d", time, tps, mspt, memoryMb, players);
        }

        static Sample parse(String line) {
            String[] p = line.split(",");
            return new Sample(Long.parseLong(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Long.parseLong(p[3]), Integer.parseInt(p[4]));
        }
    }

    public static final long KEEP_MS = 7L * 24 * 60 * 60 * 1000;

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final File file;
    private final Logger log;
    private int appendedSinceCompact;

    public History(File file, Logger log) {
        this.file = file;
        this.log = log;
        load(System.currentTimeMillis());
    }

    public synchronized void add(Sample s) {
        samples.addLast(s);
        prune(s.time());
        if (file == null) {
            return;
        }
        // The file is rewritten without old lines now and then; in between, lines are appended.
        if (++appendedSinceCompact > 24 * 60) {
            compact();
            return;
        }
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(s.csv());
            w.newLine();
        } catch (IOException e) {
            log.warning("Could not save the performance history: " + e.getMessage());
        }
    }

    public synchronized List<Sample> samples() {
        return new ArrayList<>(samples);
    }

    /**
     * Samples of the last [rangeMs], averaged into at most [maxPoints] buckets (players: maximum,
     * so short peaks stay visible). Each point: [time, tps, mspt, memoryMb, players]; tps or mspt
     * is -1 if the server does not report it.
     */
    public synchronized JsonObject json(long rangeMs, int maxPoints, long now) {
        long from = now - rangeMs;
        long step = Math.max(60_000L, rangeMs / maxPoints);
        JsonArray points = new JsonArray();
        long bucket = -1;
        double tps = 0, mspt = 0, mem = 0;
        int tpsN = 0, msptN = 0, n = 0, players = 0;
        for (Sample s : samples) {
            if (s.time() < from) {
                continue;
            }
            long b = from + (s.time() - from) / step * step;
            if (b != bucket && n > 0) {
                points.add(point(bucket, tps, tpsN, mspt, msptN, mem, n, players));
                tps = mspt = mem = 0;
                tpsN = msptN = n = players = 0;
            }
            bucket = b;
            if (s.tps() >= 0) { tps += s.tps(); tpsN++; }
            if (s.mspt() >= 0) { mspt += s.mspt(); msptN++; }
            mem += s.memoryMb();
            players = Math.max(players, s.players());
            n++;
        }
        if (n > 0) {
            points.add(point(bucket, tps, tpsN, mspt, msptN, mem, n, players));
        }
        JsonObject o = new JsonObject();
        o.addProperty("from", from);
        o.addProperty("to", now);
        o.addProperty("step", step);
        o.add("points", points);
        return o;
    }

    private static JsonArray point(long time, double tps, int tpsN, double mspt, int msptN, double mem, int n, int players) {
        JsonArray p = new JsonArray();
        p.add(time);
        p.add(tpsN == 0 ? -1 : Math.round(tps / tpsN * 100) / 100.0);
        p.add(msptN == 0 ? -1 : Math.round(mspt / msptN * 100) / 100.0);
        p.add(Math.round(mem / n));
        p.add(players);
        return p;
    }

    private void prune(long now) {
        while (!samples.isEmpty() && samples.peekFirst().time() < now - KEEP_MS) {
            samples.removeFirst();
        }
    }

    private void load(long now) {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                try {
                    Sample s = Sample.parse(line.trim());
                    if (s.time() >= now - KEEP_MS && (samples.isEmpty() || s.time() >= samples.peekLast().time())) {
                        samples.addLast(s);
                    }
                } catch (RuntimeException ignored) {
                    // skip broken lines
                }
            }
            compact();
        } catch (IOException e) {
            log.warning("Could not read the performance history: " + e.getMessage());
        }
    }

    private void compact() {
        appendedSinceCompact = 0;
        List<String> lines = new ArrayList<>(samples.size());
        for (Sample s : samples) {
            lines.add(s.csv());
        }
        try {
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warning("Could not save the performance history: " + e.getMessage());
        }
    }
}
