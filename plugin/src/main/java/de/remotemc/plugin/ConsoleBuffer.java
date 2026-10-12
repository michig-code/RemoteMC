package de.remotemc.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Mirrors the server console into a ring buffer so the app can poll new lines.
 */
final class ConsoleBuffer {

    record Line(long id, long time, String level, String logger, String message) {}

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m|§[0-9a-fk-orx]");

    private final Deque<Line> lines = new ArrayDeque<>();
    private int capacity;
    private long nextId = 1;
    private Appender appender;

    ConsoleBuffer(int capacity) {
        this.capacity = Math.max(50, capacity);
    }

    void attach() {
        appender = new Appender();
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
    }

    void detach() {
        if (appender != null) {
            ((Logger) LogManager.getRootLogger()).removeAppender(appender);
            appender.stop();
            appender = null;
        }
    }

    synchronized void resize(int capacity) {
        this.capacity = Math.max(50, capacity);
        trim();
    }

    synchronized void add(String level, String logger, String message) {
        lines.addLast(new Line(nextId++, System.currentTimeMillis(), level, logger, ANSI.matcher(message).replaceAll("")));
        trim();
    }

    private void trim() {
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    /** Lines with an id greater than {@code since}. */
    synchronized List<Line> since(long since) {
        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            if (line.id() > since) {
                result.add(line);
            }
        }
        return result;
    }

    synchronized long lastId() {
        return nextId - 1;
    }

    private final class Appender extends AbstractAppender {
        Appender() {
            super("RemoteMCConsole", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            String message = event.getMessage().getFormattedMessage();
            if (event.getThrown() != null) {
                message += "\n" + event.getThrown();
            }
            String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
            add(event.getLevel().name(), loggerName, message);
        }
    }
}
