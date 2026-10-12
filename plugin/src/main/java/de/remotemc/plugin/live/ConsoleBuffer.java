package de.remotemc.plugin.live;

import com.google.gson.JsonObject;
import de.remotemc.plugin.auth.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.UnaryOperator;

/** Mirrors the server console into a ring buffer and the live event stream. */
public final class ConsoleBuffer {

    /** {@code formatted} keeps colours as section codes (§a); {@code message} is plain text. */
    public record Line(long id, long time, String level, String message, String formatted) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("time", time);
            o.addProperty("level", level);
            o.addProperty("message", message);
            if (!formatted.equals(message)) {
                o.addProperty("formatted", formatted);
            }
            return o;
        }
    }

    private final EventHub hub;
    private final UnaryOperator<String> redactor;
    private final Deque<Line> lines = new ArrayDeque<>();
    private int capacity;
    private long nextId = 1;
    private Appender appender;

    /** @param redactor hides secrets (tokens, pairing codes) before lines reach the app */
    public ConsoleBuffer(EventHub hub, int capacity, UnaryOperator<String> redactor) {
        this.hub = hub;
        this.redactor = redactor;
        this.capacity = Math.max(50, capacity);
    }

    public void attach() {
        appender = new Appender();
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
    }

    public void detach() {
        if (appender != null) {
            ((Logger) LogManager.getRootLogger()).removeAppender(appender);
            appender.stop();
            appender = null;
        }
    }

    public synchronized void resize(int capacity) {
        this.capacity = Math.max(50, capacity);
        trim();
    }

    void add(String level, String message) {
        Line line;
        synchronized (this) {
            String formatted = redactor.apply(Formatting.toSectionCodes(message));
            line = new Line(nextId++, System.currentTimeMillis(), level, Formatting.strip(formatted), formatted);
            lines.addLast(line);
            trim();
        }
        hub.publish("console", line.toJson(), Role.MODERATOR);
    }

    private void trim() {
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    public synchronized List<Line> since(long since) {
        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            if (line.id() > since) {
                result.add(line);
            }
        }
        return result;
    }

    public synchronized long lastId() {
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
            add(event.getLevel().name(), message);
        }
    }
}
