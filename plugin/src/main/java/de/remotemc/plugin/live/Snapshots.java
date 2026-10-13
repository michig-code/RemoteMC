package de.remotemc.plugin.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.remotemc.plugin.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Builds the status and player JSON. Call on the main / global region thread. */
public final class Snapshots {

    public static final String[] FEATURES = {"history", "moderation", "worlds", "plugins", "schedules", "teleport", "files"};

    private final long startedAt = System.currentTimeMillis();
    private final String pluginVersion;

    public Snapshots(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("motd", Platform.motd());
        o.addProperty("version", Bukkit.getVersion());
        o.addProperty("minecraftVersion", Platform.minecraftVersion());
        o.addProperty("platform", Platform.name());
        o.addProperty("pluginVersion", pluginVersion);
        // What this plugin version can do, so apps hide what an older plugin lacks.
        JsonArray features = new JsonArray();
        for (String f : FEATURES) {
            features.add(f);
        }
        o.add("features", features);
        o.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        o.addProperty("maxPlayers", Bukkit.getMaxPlayers());

        JsonArray tps = new JsonArray();
        for (double d : Platform.tps()) {
            tps.add(Math.min(20.0, Math.round(d * 100.0) / 100.0));
        }
        o.add("tps", tps);
        double mspt = Platform.mspt();
        o.addProperty("mspt", mspt < 0 ? -1 : Math.round(mspt * 100.0) / 100.0);

        Runtime rt = Runtime.getRuntime();
        o.addProperty("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        o.addProperty("memoryMaxMb", rt.maxMemory() / 1024 / 1024);
        o.addProperty("uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000);

        JsonArray worlds = new JsonArray();
        for (World w : Bukkit.getWorlds()) {
            JsonObject wo = new JsonObject();
            wo.addProperty("name", w.getName());
            wo.addProperty("environment", w.getEnvironment().name());
            wo.addProperty("players", w.getPlayers().size());
            // Folia does not allow world-wide queries from the global thread.
            wo.addProperty("loadedChunks", safe(() -> w.getLoadedChunks().length));
            wo.addProperty("entities", safe(() -> Platform.entityCount(w)));
            wo.addProperty("time", w.getTime());
            wo.addProperty("storm", w.hasStorm());
            worlds.add(wo);
        }
        o.add("worlds", worlds);
        return o;
    }

    public JsonArray players() {
        JsonArray arr = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            Location loc = p.getLocation();
            o.addProperty("name", p.getName());
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("world", loc.getWorld() == null ? "" : loc.getWorld().getName());
            o.addProperty("x", loc.getBlockX());
            o.addProperty("y", loc.getBlockY());
            o.addProperty("z", loc.getBlockZ());
            o.addProperty("health", Math.round(p.getHealth() * 10.0) / 10.0);
            o.addProperty("maxHealth", Math.round(Platform.maxHealth(p) * 10.0) / 10.0);
            o.addProperty("food", p.getFoodLevel());
            o.addProperty("level", p.getLevel());
            o.addProperty("ping", p.getPing());
            o.addProperty("gameMode", p.getGameMode().name());
            o.addProperty("op", p.isOp());
            o.addProperty("playtime", safe(() -> p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20));
            String skin = skinUrl(p);
            if (skin != null) {
                o.addProperty("skin", skin);
            }
            arr.add(o);
        }
        return arr;
    }

    /** Status + players in one object (used for the live stream). */
    public JsonObject full() {
        JsonObject o = new JsonObject();
        o.add("status", status());
        o.add("players", players());
        return o;
    }

    /**
     * The player's skin texture from their profile (as Mojang delivered it at login, so it also
     * reflects skins set by plugins like SkinsRestorer). The app cuts the face from it.
     */
    private static String skinUrl(Player p) {
        try {
            java.net.URL url = p.getPlayerProfile().getTextures().getSkin();
            return url == null ? null : url.toString().replaceFirst("^http://", "https://");
        } catch (Throwable t) {
            return null;
        }
    }

    private static int safe(java.util.function.IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (Throwable t) {
            return -1;
        }
    }
}
