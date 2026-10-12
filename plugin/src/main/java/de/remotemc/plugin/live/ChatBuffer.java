package de.remotemc.plugin.live;

import com.google.gson.JsonObject;
import de.remotemc.plugin.auth.Role;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Recent chat, joins/leaves and broadcasts for the app's chat tab. */
public final class ChatBuffer {

    public enum Type { CHAT, JOIN, QUIT, BROADCAST }

    public record Entry(long id, long time, Type type, String player, String message) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("time", time);
            o.addProperty("type", type.name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("player", player);
            o.addProperty("message", message);
            return o;
        }
    }

    private static final int CAPACITY = 200;

    private final EventHub hub;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private long nextId = 1;

    public ChatBuffer(EventHub hub) {
        this.hub = hub;
    }

    public void add(Type type, String player, String message) {
        Entry e;
        synchronized (this) {
            e = new Entry(nextId++, System.currentTimeMillis(), type, player, message);
            entries.addLast(e);
            while (entries.size() > CAPACITY) {
                entries.removeFirst();
            }
        }
        hub.publish("chat", e.toJson(), Role.VIEWER);
    }

    public synchronized List<Entry> since(long since) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries) {
            if (e.id() > since) {
                result.add(e);
            }
        }
        return result;
    }

    public synchronized long lastId() {
        return nextId - 1;
    }
}
