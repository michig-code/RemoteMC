package de.remotemc.plugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/** Time, weather, difficulty and game rules of one world. Call on the global region thread. */
final class WorldControl {

    private WorldControl() {
    }

    static World world(String name) throws ApiException {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            throw ApiException.notFound("world_unknown", "No world named " + name);
        }
        return w;
    }

    static JsonObject describe(World w) {
        JsonObject o = new JsonObject();
        o.addProperty("name", w.getName());
        o.addProperty("environment", w.getEnvironment().name());
        o.addProperty("time", w.getTime());
        o.addProperty("storm", w.hasStorm());
        o.addProperty("thunder", w.isThundering());
        o.addProperty("difficulty", w.getDifficulty().name());
        JsonObject spawn = new JsonObject();
        spawn.addProperty("x", w.getSpawnLocation().getBlockX());
        spawn.addProperty("y", w.getSpawnLocation().getBlockY());
        spawn.addProperty("z", w.getSpawnLocation().getBlockZ());
        o.add("spawn", spawn);
        JsonArray rules = new JsonArray();
        GameRule<?>[] all = GameRule.values();
        Arrays.sort(all, Comparator.comparing(GameRule::getName));
        for (GameRule<?> rule : all) {
            Object value;
            try {
                value = w.getGameRuleValue(rule);
            } catch (RuntimeException e) {
                continue; // experimental rules that are not enabled in this world
            }
            if (value == null) {
                continue;
            }
            JsonObject r = new JsonObject();
            r.addProperty("name", rule.getName());
            if (value instanceof Boolean b) {
                r.addProperty("type", "boolean");
                r.addProperty("value", b);
            } else if (value instanceof Integer i) {
                r.addProperty("type", "int");
                r.addProperty("value", i);
            } else {
                continue;
            }
            rules.add(r);
        }
        o.add("gamerules", rules);
        return o;
    }

    /** Nether and End have no day and night and no weather. */
    private static void requireSky(World w) throws ApiException {
        if (w.getEnvironment() == World.Environment.NETHER || w.getEnvironment() == World.Environment.THE_END) {
            throw ApiException.badRequest("no_sky", "The Nether and the End have no time of day and no weather");
        }
    }

    static void setTime(World w, String time) throws ApiException {
        requireSky(w);
        long ticks = switch (time) {
            case "morning" -> 0;
            case "day" -> 1000;
            case "noon" -> 6000;
            case "evening" -> 12000;
            case "night" -> 13000;
            case "midnight" -> 18000;
            default -> throw ApiException.badRequest("bad_request", "Unknown time: " + time);
        };
        w.setTime(ticks);
    }

    static void setWeather(World w, String weather) throws ApiException {
        requireSky(w);
        switch (weather) {
            case "clear" -> {
                w.setStorm(false);
                w.setThundering(false);
                w.setClearWeatherDuration(12000);
            }
            case "rain" -> {
                w.setStorm(true);
                w.setThundering(false);
                w.setWeatherDuration(12000);
            }
            case "thunder" -> {
                w.setStorm(true);
                w.setThundering(true);
                w.setThunderDuration(12000);
                w.setWeatherDuration(12000);
            }
            default -> throw ApiException.badRequest("bad_request", "Unknown weather: " + weather);
        }
    }

    static void setDifficulty(World w, String difficulty) throws ApiException {
        try {
            w.setDifficulty(Difficulty.valueOf(difficulty.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("bad_request", "Unknown difficulty: " + difficulty);
        }
    }

    @SuppressWarnings("unchecked")
    static void setGameRule(World w, String name, JsonElement value) throws ApiException {
        GameRule<?> rule = GameRule.getByName(name);
        if (rule == null || value == null || !value.isJsonPrimitive()) {
            throw ApiException.badRequest("bad_request", "Unknown game rule or value: " + name);
        }
        boolean ok;
        if (rule.getType() == Boolean.class) {
            ok = w.setGameRule((GameRule<Boolean>) rule, value.getAsBoolean());
        } else if (rule.getType() == Integer.class) {
            int v;
            try {
                v = value.getAsInt();
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("bad_request", "Not a number: " + value);
            }
            ok = w.setGameRule((GameRule<Integer>) rule, v);
        } else {
            throw ApiException.badRequest("bad_request", "Unsupported game rule: " + name);
        }
        if (!ok) {
            throw ApiException.badRequest("bad_request", "Invalid value for " + name);
        }
    }
}
