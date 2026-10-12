package de.remotemc.plugin.live;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.remotemc.plugin.auth.Role;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Fan-out of live events to connected app streams (Server-Sent Events). */
public final class EventHub {

    private static final Gson GSON = new Gson();
    private static final int QUEUE_LIMIT = 2000;

    /** One SSE frame, visible to subscribers with at least {@code minRole}. */
    record Frame(Role minRole, String text) {}

    public final class Subscription implements AutoCloseable {
        private final Role role;
        private final BlockingQueue<Frame> queue = new LinkedBlockingQueue<>(QUEUE_LIMIT);
        private volatile boolean overflowed;

        private Subscription(Role role) {
            this.role = role;
        }

        /** Next frame text for this subscriber, or null after the timeout (send a heartbeat then). */
        public String next(long timeoutMillis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                long left = deadline - System.nanoTime();
                Frame f = queue.poll(Math.max(0, left), TimeUnit.NANOSECONDS);
                if (f == null) {
                    return null;
                }
                if (role.atLeast(f.minRole())) {
                    return f.text();
                }
            }
        }

        /** A client that cannot keep up is disconnected; it reconnects and resyncs. */
        public boolean overflowed() {
            return overflowed;
        }

        void offer(Frame frame) {
            if (!queue.offer(frame)) {
                overflowed = true;
            }
        }

        @Override
        public void close() {
            subscribers.remove(this);
        }
    }

    private final Set<Subscription> subscribers = new CopyOnWriteArraySet<>();

    public Subscription subscribe(Role role) {
        Subscription s = new Subscription(role);
        subscribers.add(s);
        return s;
    }

    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public void publish(String event, JsonObject data, Role minRole) {
        if (subscribers.isEmpty()) {
            return;
        }
        Frame frame = new Frame(minRole, frame(event, data));
        for (Subscription s : subscribers) {
            s.offer(frame);
        }
    }

    public static String frame(String event, JsonObject data) {
        return "event: " + event + "\ndata: " + GSON.toJson(data) + "\n\n";
    }
}
