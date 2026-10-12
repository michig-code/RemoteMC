package de.remotemc.plugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Whitelist and ban list as JSON. Call on the main / global region thread. */
final class Moderation {

    private Moderation() {
    }

    static JsonObject whitelist() {
        List<String> names = new ArrayList<>();
        for (OfflinePlayer p : Bukkit.getWhitelistedPlayers()) {
            if (p.getName() != null) {
                names.add(p.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        JsonArray players = new JsonArray();
        names.forEach(players::add);
        JsonObject o = new JsonObject();
        o.addProperty("enabled", Bukkit.hasWhitelist());
        o.add("players", players);
        return o;
    }

    static boolean isWhitelisted(String name) {
        for (OfflinePlayer p : Bukkit.getWhitelistedPlayers()) {
            if (name.equalsIgnoreCase(p.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Player bans (newest first): name, reason, source, created and expires (ms, or absent). */
    static JsonObject bans() {
        BanList<?> list = Bukkit.getBanList(BanList.Type.PROFILE);
        List<BanEntry<?>> entries = new ArrayList<>(list.getEntries());
        entries.sort(Comparator.comparing((BanEntry<?> e) -> e.getCreated() == null ? new Date(0) : e.getCreated()).reversed());
        JsonArray arr = new JsonArray();
        for (BanEntry<?> e : entries) {
            String name = targetName(e.getTarget());
            if (name == null) {
                continue;
            }
            JsonObject b = new JsonObject();
            b.addProperty("name", name);
            if (e.getReason() != null) {
                b.addProperty("reason", e.getReason());
            }
            if (e.getSource() != null) {
                b.addProperty("source", e.getSource());
            }
            if (e.getCreated() != null) {
                b.addProperty("created", e.getCreated().getTime());
            }
            if (e.getExpiration() != null) {
                b.addProperty("expires", e.getExpiration().getTime());
            }
            arr.add(b);
        }
        JsonObject o = new JsonObject();
        o.add("bans", arr);
        return o;
    }

    static boolean isBanned(String name) {
        BanList<?> list = Bukkit.getBanList(BanList.Type.PROFILE);
        for (BanEntry<?> e : list.getEntries()) {
            if (name.equalsIgnoreCase(targetName(e.getTarget()))) {
                return true;
            }
        }
        return false;
    }

    private static String targetName(Object target) {
        if (target instanceof PlayerProfile profile) {
            return profile.getName();
        }
        if (target instanceof String s) {
            return s;
        }
        return target == null ? null : target.toString().toLowerCase(Locale.ROOT);
    }
}
