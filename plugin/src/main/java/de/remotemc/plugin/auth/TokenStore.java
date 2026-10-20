package de.remotemc.plugin.auth;

import de.remotemc.plugin.core.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Access tokens, stored in tokens.yml as SHA-256 hashes only. The plain token is
 * shown exactly once (on creation) and afterwards lives only on the device.
 */
public final class TokenStore {

    public record Token(String id, String name, Role role, String hash, long created, long lastUsed) {
        Token withLastUsed(long time) {
            return new Token(id, name, role, hash, created, time);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final File file;
    private final Logger logger;
    private final Map<String, Token> byHash = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public TokenStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public synchronized void load() {
        byHash.clear();
        Map<String, Object> section = Yaml.section(Yaml.load(file), "tokens");
        if (section == null) {
            return;
        }
        for (String id : section.keySet()) {
            Map<String, Object> t = Yaml.section(section, id);
            Role role = t == null ? null : Role.parse(t.get("role") == null ? null : String.valueOf(t.get("role")));
            String hash = t == null || t.get("hash") == null ? null : String.valueOf(t.get("hash"));
            if (role == null || hash == null) {
                logger.warning("Ignoring invalid token entry '" + id + "' in tokens.yml");
                continue;
            }
            String name = t.get("name") == null ? id : String.valueOf(t.get("name"));
            byHash.put(hash, new Token(id, name, role, hash, number(t.get("created")), number(t.get("last-used"))));
        }
    }

    public synchronized void save() {
        Map<String, Object> tokens = new LinkedHashMap<>();
        for (Token t : byHash.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", t.name());
            entry.put("role", t.role().id());
            entry.put("hash", t.hash());
            entry.put("created", t.created());
            entry.put("last-used", t.lastUsed());
            tokens.put(t.id(), entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tokens", tokens);
        try {
            Yaml.save(file, List.of(
                    "RemoteMC access tokens (only hashes are stored).",
                    "Manage them with /remotemc token list | revoke <name>"), root);
            dirty = false;
        } catch (IOException e) {
            logger.severe("Could not save tokens.yml: " + e.getMessage());
        }
    }

    /** Persists last-used timestamps (called periodically, not on every request). */
    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /** Creates a token and returns its plain value. Name is made unique automatically. */
    public synchronized String create(String requestedName, Role role) {
        String name = uniqueName(requestedName);
        String plain = "rmc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        String id = UUID.randomUUID().toString().substring(0, 8);
        byHash.put(hash(plain), new Token(id, name, role, hash(plain), System.currentTimeMillis(), 0));
        save();
        return plain;
    }

    /** Imports a legacy plain token (from the old config.yml) as admin. */
    public synchronized void importLegacy(String plain) {
        if (byHash.containsKey(hash(plain))) {
            return;
        }
        byHash.put(hash(plain), new Token("legacy", "Legacy token", Role.ADMIN, hash(plain), System.currentTimeMillis(), 0));
        save();
    }

    public boolean nameExists(String name) {
        return byHash.values().stream().anyMatch(t -> t.name().equalsIgnoreCase(name));
    }

    private String uniqueName(String requested) {
        String base = requested == null || requested.isBlank() ? "Device" : requested.strip();
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String name = base;
        for (int i = 2; nameExists(name); i++) {
            name = base + " (" + i + ")";
        }
        return name;
    }

    /** Looks up a presented token by its hash, so lookup timing reveals nothing about the token itself. */
    public Token authenticate(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        Token t = byHash.get(hash(plain));
        if (t == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        // Only mark dirty when the stored value is noticeably stale.
        if (now - t.lastUsed() > 60_000) {
            byHash.put(t.hash(), t.withLastUsed(now));
            dirty = true;
        }
        return byHash.get(t.hash());
    }

    public synchronized boolean revoke(String name) {
        boolean removed = byHash.values().removeIf(t -> t.name().equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<Token> all() {
        List<Token> list = new ArrayList<>(byHash.values());
        list.sort((a, b) -> a.name().toLowerCase(Locale.ROOT).compareTo(b.name().toLowerCase(Locale.ROOT)));
        return list;
    }

    public int size() {
        return byHash.size();
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    static String hash(String plain) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
