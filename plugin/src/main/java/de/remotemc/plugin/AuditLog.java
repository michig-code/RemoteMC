package de.remotemc.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Appends one line per app action to audit.log, off the calling thread. */
public final class AuditLog {

    private final File file;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RemoteMC-Audit");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean enabled;

    public AuditLog(File file, Logger logger, boolean enabled) {
        this.file = file;
        this.logger = logger;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String who, String ip, String action) {
        if (!enabled) {
            return;
        }
        String line = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + " [" + clean(who) + "] " + ip + " " + clean(action) + System.lineSeparator();
        writer.execute(() -> {
            try (Writer w = new FileWriter(file, StandardCharsets.UTF_8, true)) {
                w.write(line);
            } catch (IOException e) {
                logger.warning("Could not write audit.log: " + e.getMessage());
            }
        });
    }

    public void close() {
        writer.shutdown();
    }

    /** One line per entry: control characters (line breaks, escapes) would allow fake entries. */
    static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\p{Cntrl}", " ");
    }
}
