package de.remotemc.plugin.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Locks out IP addresses after repeated invalid tokens or pairing codes. */
public final class LoginGuard {

    private static final class Entry {
        int failures;
        long lockedUntil;
        long lastFailure;
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile int maxFailures;
    private volatile long lockoutMillis;

    public LoginGuard(int maxFailures, long lockoutSeconds) {
        configure(maxFailures, lockoutSeconds);
    }

    public void configure(int maxFailures, long lockoutSeconds) {
        this.maxFailures = Math.max(1, maxFailures);
        this.lockoutMillis = Math.max(1, lockoutSeconds) * 1000L;
    }

    public boolean isLocked(String ip) {
        Entry e = entries.get(ip);
        return e != null && e.lockedUntil > System.currentTimeMillis();
    }

    /** Records a failure; returns true if the IP is now locked out. */
    public boolean fail(String ip) {
        long now = System.currentTimeMillis();
        cleanup(now);
        Entry e = entries.computeIfAbsent(ip, k -> new Entry());
        synchronized (e) {
            // Forget old failures after one lockout period.
            if (now - e.lastFailure > lockoutMillis) {
                e.failures = 0;
            }
            e.lastFailure = now;
            if (++e.failures >= maxFailures) {
                e.failures = 0;
                e.lockedUntil = now + lockoutMillis;
                return true;
            }
        }
        return false;
    }

    public void success(String ip) {
        entries.remove(ip);
    }

    private void cleanup(long now) {
        if (entries.size() > 1000) {
            entries.values().removeIf(e -> e.lockedUntil < now && now - e.lastFailure > lockoutMillis);
        }
    }
}
