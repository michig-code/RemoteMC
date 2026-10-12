package de.remotemc.plugin.auth;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Short-lived one-time pairing codes. The app exchanges a code for its own token,
 * so long-lived secrets never appear in chat, screenshots or QR codes.
 */
public final class PairingManager {

    public record Code(String code, Role role, long expiresAt, String createdBy) {}

    /** Wrong guesses (across all IPs) before every open code is invalidated. */
    private static final int MAX_GLOBAL_FAILURES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Code> codes = new ConcurrentHashMap<>();
    private final AtomicInteger failures = new AtomicInteger();

    public Code create(Role role, int validMinutes, String createdBy) {
        purgeExpired();
        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (codes.containsKey(code));
        Code c = new Code(code, role, System.currentTimeMillis() + validMinutes * 60_000L, createdBy);
        codes.put(code, c);
        failures.set(0);
        return c;
    }

    /** Consumes a code. Returns null if it is unknown, used or expired. */
    public Code redeem(String code) {
        purgeExpired();
        Code c = code == null ? null : codes.remove(code.trim());
        if (c == null) {
            if (failures.incrementAndGet() >= MAX_GLOBAL_FAILURES) {
                codes.clear();
                failures.set(0);
            }
            return null;
        }
        return c;
    }

    /** Replaces open pairing codes in a text, so they cannot be read from the console stream. */
    public String redact(String text) {
        String result = text;
        for (String code : codes.keySet()) {
            result = result.replace(code, "••••••");
        }
        return result;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Code> it = codes.values().iterator(); it.hasNext(); ) {
            if (it.next().expiresAt() < now) {
                it.remove();
            }
        }
    }
}
