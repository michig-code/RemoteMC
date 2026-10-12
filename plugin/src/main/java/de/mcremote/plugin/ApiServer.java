package de.mcremote.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Small REST API on top of the JDK HTTP server. All requests except /api/ping
 * need the header {@code Authorization: Bearer <token>}.
 */
final class ApiServer {

    private static final Gson GSON = new Gson();
    private static final int MAX_BODY = 16 * 1024;

    private final McRemotePlugin plugin;
    private final ConsoleBuffer console;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    private static final class Failures {
        int count;
        long lockedUntil;
    }

    private static final class ApiException extends Exception {
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    ApiServer(McRemotePlugin plugin, ConsoleBuffer console) {
        this.plugin = plugin;
        this.console = console;
    }

    void start() throws IOException {
        String host = plugin.getConfig().getString("api.host", "0.0.0.0");
        int port = plugin.getConfig().getInt("api.port", 8765);
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "McRemote-API");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/api/", this::handle);
        server.start();
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath().replaceAll("/+$", "");
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);

            if (path.equals("/api/ping")) {
                JsonObject o = new JsonObject();
                o.addProperty("app", "McRemote");
                o.addProperty("version", plugin.getPluginMeta().getVersion());
                send(ex, 200, o);
                return;
            }

            authenticate(ex);

            JsonObject result = route(method, path, ex);
            send(ex, 200, result);
        } catch (ApiException e) {
            sendError(ex, e.status, e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("API error: " + e);
            sendError(ex, 500, "Interner Fehler: " + e.getMessage());
        } finally {
            ex.close();
        }
    }

    private JsonObject route(String method, String path, HttpExchange ex) throws Exception {
        String[] parts = path.split("/");
        // parts: ["", "api", ...]
        if (method.equals("GET") && path.equals("/api/status")) {
            return sync(this::status);
        }
        if (method.equals("GET") && path.equals("/api/players")) {
            return sync(this::players);
        }
        if (method.equals("GET") && path.equals("/api/console")) {
            return consoleSince(queryParam(ex, "since"));
        }
        if (method.equals("POST") && path.equals("/api/command")) {
            return runCommand(stringField(readBody(ex), "command"));
        }
        if (method.equals("POST") && path.equals("/api/chat")) {
            String message = stringField(readBody(ex), "message");
            return sync(() -> {
                Bukkit.broadcast(Component.text("[Server] " + message));
                return ok();
            });
        }
        if (method.equals("POST") && parts.length == 5 && parts[2].equals("players")) {
            String name = parts[3];
            String action = parts[4];
            JsonObject body = readBody(ex);
            return sync(() -> playerAction(name, action, body));
        }
        throw new ApiException(404, "Unbekannter Endpunkt: " + method + " " + path);
    }

    // ── Auth ────────────────────────────────────────────────────────────

    private void authenticate(HttpExchange ex) throws ApiException {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        Failures f = failures.get(ip);
        long now = System.currentTimeMillis();
        if (f != null && f.lockedUntil > now) {
            throw new ApiException(429, "Zu viele Fehlversuche. Bitte später erneut versuchen.");
        }

        String header = ex.getRequestHeaders().getFirst("Authorization");
        String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        String expected = plugin.getConfig().getString("api.token", "");

        boolean valid = !expected.isEmpty() && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        if (valid) {
            failures.remove(ip);
            return;
        }

        Failures entry = failures.computeIfAbsent(ip, k -> new Failures());
        synchronized (entry) {
            entry.count++;
            if (entry.count >= plugin.getConfig().getInt("security.max-failed-attempts", 5)) {
                entry.lockedUntil = now + plugin.getConfig().getLong("security.lockout-seconds", 300) * 1000L;
                entry.count = 0;
                plugin.getLogger().warning("McRemote: locked out " + ip + " after too many invalid tokens");
            }
        }
        throw new ApiException(401, "Ungültiger Token");
    }

    // ── Endpoints ───────────────────────────────────────────────────────

    private JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("motd", PlainTextComponentSerializer.plainText().serialize(Bukkit.motd()));
        o.addProperty("version", Bukkit.getVersion());
        o.addProperty("minecraftVersion", Bukkit.getMinecraftVersion());
        o.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        o.addProperty("maxPlayers", Bukkit.getMaxPlayers());

        JsonArray tps = new JsonArray();
        for (double d : Bukkit.getTPS()) {
            tps.add(Math.min(20.0, Math.round(d * 100.0) / 100.0));
        }
        o.add("tps", tps);
        o.addProperty("mspt", Math.round(Bukkit.getAverageTickTime() * 100.0) / 100.0);

        Runtime rt = Runtime.getRuntime();
        o.addProperty("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        o.addProperty("memoryMaxMb", rt.maxMemory() / 1024 / 1024);
        o.addProperty("uptimeSeconds", (System.currentTimeMillis() - plugin.startedAt()) / 1000);

        JsonArray worlds = new JsonArray();
        for (World w : Bukkit.getWorlds()) {
            JsonObject wo = new JsonObject();
            wo.addProperty("name", w.getName());
            wo.addProperty("environment", w.getEnvironment().name());
            wo.addProperty("players", w.getPlayers().size());
            wo.addProperty("loadedChunks", w.getLoadedChunks().length);
            wo.addProperty("entities", w.getEntityCount());
            wo.addProperty("time", w.getTime());
            wo.addProperty("storm", w.hasStorm());
            worlds.add(wo);
        }
        o.add("worlds", worlds);
        return o;
    }

    private JsonObject players() {
        JsonArray arr = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName());
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("world", p.getWorld().getName());
            o.addProperty("x", p.getLocation().getBlockX());
            o.addProperty("y", p.getLocation().getBlockY());
            o.addProperty("z", p.getLocation().getBlockZ());
            o.addProperty("health", Math.round(p.getHealth() * 10.0) / 10.0);
            o.addProperty("food", p.getFoodLevel());
            o.addProperty("level", p.getLevel());
            o.addProperty("ping", p.getPing());
            o.addProperty("gameMode", p.getGameMode().name());
            o.addProperty("op", p.isOp());
            arr.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("players", arr);
        return o;
    }

    private JsonObject playerAction(String name, String action, JsonObject body) throws ApiException {
        Player p = Bukkit.getPlayerExact(name);
        String reason = body.has("reason") && !body.get("reason").isJsonNull()
                ? body.get("reason").getAsString() : "";
        switch (action) {
            case "kick" -> {
                requireOnline(p, name).kick(Component.text(reason.isBlank() ? "Vom Server geworfen" : reason));
            }
            case "ban" -> {
                // The vanilla command resolves offline players by name as well.
                if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                    throw new ApiException(400, "Ungültiger Spielername");
                }
                String cmd = "minecraft:ban " + name + (reason.isBlank() ? "" : " " + reason.replace('\n', ' '));
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) {
                    throw new ApiException(500, "Bann fehlgeschlagen");
                }
            }
            case "op" -> requireOnline(p, name).setOp(true);
            case "deop" -> requireOnline(p, name).setOp(false);
            case "gamemode" -> {
                String mode = stringField(body, "mode").toUpperCase(Locale.ROOT);
                try {
                    requireOnline(p, name).setGameMode(GameMode.valueOf(mode));
                } catch (IllegalArgumentException e) {
                    throw new ApiException(400, "Unbekannter Spielmodus: " + mode);
                }
            }
            case "heal" -> {
                Player target = requireOnline(p, name);
                target.setHealth(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                target.setFoodLevel(20);
                target.setSaturation(20f);
            }
            case "message" -> requireOnline(p, name).sendMessage(Component.text("[Server] " + stringField(body, "message")));
            default -> throw new ApiException(404, "Unbekannte Aktion: " + action);
        }
        plugin.getLogger().info("McRemote: " + action + " " + name);
        return ok();
    }

    private Player requireOnline(Player p, String name) throws ApiException {
        if (p == null) {
            throw new ApiException(404, "Spieler " + name + " ist nicht online");
        }
        return p;
    }

    private JsonObject runCommand(String command) throws Exception {
        if (!plugin.getConfig().getBoolean("security.allow-commands", true)) {
            throw new ApiException(403, "Befehle sind in der config.yml deaktiviert");
        }
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        if (cmd.isBlank()) {
            throw new ApiException(400, "Leerer Befehl");
        }
        String label = cmd.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        for (String blocked : plugin.getConfig().getStringList("security.blocked-commands")) {
            if (blocked.equalsIgnoreCase(label)) {
                throw new ApiException(403, "Befehl '" + label + "' ist gesperrt");
            }
        }

        List<String> output = new ArrayList<>();
        boolean success = sync(() -> {
            CommandSender sender = Bukkit.createCommandSender(component -> {
                synchronized (output) {
                    output.add(PlainTextComponentSerializer.plainText().serialize(component));
                }
            });
            plugin.getLogger().info("McRemote executing: /" + cmd);
            return Bukkit.dispatchCommand(sender, cmd);
        });

        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        JsonArray arr = new JsonArray();
        synchronized (output) {
            output.forEach(arr::add);
        }
        o.add("output", arr);
        return o;
    }

    private JsonObject consoleSince(String sinceParam) {
        long since = 0;
        try {
            if (sinceParam != null) {
                since = Long.parseLong(sinceParam);
            }
        } catch (NumberFormatException ignored) {
            // treat as 0
        }
        JsonArray arr = new JsonArray();
        for (ConsoleBuffer.Line line : console.since(since)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", line.id());
            o.addProperty("time", line.time());
            o.addProperty("level", line.level());
            o.addProperty("message", line.message());
            arr.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("lines", arr);
        o.addProperty("last", console.lastId());
        return o;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private <T> T sync(Callable<T> task) throws Exception {
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, task).get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof ApiException api) {
                throw api;
            }
            throw e.getCause() instanceof Exception c ? c : e;
        }
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) {
            return null;
        }
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(key)) {
                return kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private static JsonObject readBody(HttpExchange ex) throws IOException, ApiException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) {
                throw new ApiException(413, "Anfrage zu groß");
            }
            if (data.length == 0) {
                return new JsonObject();
            }
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ApiException(400, "Ungültiges JSON");
        }
    }

    private static String stringField(JsonObject body, String key) throws ApiException {
        if (!body.has(key) || body.get(key).isJsonNull()) {
            throw new ApiException(400, "Feld '" + key + "' fehlt");
        }
        return body.get(key).getAsString();
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        send(ex, status, o);
    }

    private static void send(HttpExchange ex, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
