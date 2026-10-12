package de.remotemc.plugin.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import de.remotemc.plugin.RemoteMCPlugin;
import de.remotemc.plugin.auth.PairingManager;
import de.remotemc.plugin.auth.Role;
import de.remotemc.plugin.auth.TokenStore;
import de.remotemc.plugin.live.ChatBuffer;
import de.remotemc.plugin.live.ConsoleBuffer;
import de.remotemc.plugin.live.EventHub;
import de.remotemc.plugin.live.Formatting;
import de.remotemc.plugin.live.History;
import de.remotemc.plugin.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * REST API (JSON) plus a Server-Sent Events stream, on the JDK HTTP(S) server.
 * Every route except /ping and /pair needs {@code Authorization: Bearer <token>}.
 */
public final class ApiServer {

    public static final int API_VERSION = 1;

    private static final Gson GSON = new Gson();
    private static final int MAX_BODY = 16 * 1024;
    private static final int MAX_STREAMS = 32;
    private static final long HEARTBEAT_MILLIS = 15_000;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.]{1,16}");

    private final RemoteMCPlugin plugin;
    private final AtomicInteger openStreams = new AtomicInteger();
    private HttpServer server;
    private ExecutorService executor;

    /** Authenticated caller of a request. */
    private record Caller(String name, Role role, String ip) {
        String who() {
            return name + "/" + role.id();
        }
    }

    public ApiServer(RemoteMCPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        String host = plugin.getConfig().getString("api.host", "0.0.0.0");
        int port = plugin.getConfig().getInt("api.port", 8765);
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (plugin.tls() != null) {
            HttpsServer https = HttpsServer.create(address, 0);
            https.setHttpsConfigurator(new HttpsConfigurator(plugin.tls().context()));
            server = https;
        } else {
            server = HttpServer.create(address, 0);
        }
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "RemoteMC-API");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/api/", this::handle);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public int openStreams() {
        return openStreams.get();
    }

    // ── Dispatch ────────────────────────────────────────────────────────

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath().replaceAll("/+$", "");
            // Accept unversioned paths (/api/status) as the current version.
            if (!path.startsWith("/api/v")) {
                path = "/api/v" + API_VERSION + path.substring(4);
            }
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            String ip = ex.getRemoteAddress().getAddress().getHostAddress();
            ex.getResponseHeaders().set("Cache-Control", "no-store");

            if (path.equals("/api/v1/ping")) {
                send(ex, 200, ping());
                return;
            }
            if (method.equals("POST") && path.equals("/api/v1/pair")) {
                send(ex, 200, pair(readBody(ex), ip));
                return;
            }

            Caller caller = authenticate(ex, ip);
            if (method.equals("GET") && path.equals("/api/v1/events")) {
                stream(ex, caller);
                return;
            }
            send(ex, 200, route(method, path, ex, caller));
        } catch (ApiException e) {
            sendError(ex, e.status, e.code, e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("API error: " + e);
            sendError(ex, 500, "internal", "Internal error: " + e.getMessage());
        } finally {
            ex.close();
        }
    }

    private JsonObject route(String method, String path, HttpExchange ex, Caller caller) throws Exception {
        String[] parts = path.split("/"); // ["", "api", "v1", ...]
        switch (method + " " + path) {
            case "GET /api/v1/me" -> {
                return me(caller);
            }
            case "GET /api/v1/status" -> {
                return global(() -> plugin.snapshots().status());
            }
            case "GET /api/v1/history" -> {
                History history = plugin.history();
                if (history == null) {
                    throw new ApiException(404, "history_disabled", "The performance history is turned off in the config");
                }
                long range = switch (String.valueOf(param(ex, "range"))) {
                    case "1h" -> 60L * 60 * 1000;
                    case "7d" -> History.KEEP_MS;
                    default -> 24L * 60 * 60 * 1000;
                };
                return history.json(range, 360, System.currentTimeMillis());
            }
            case "GET /api/v1/players" -> {
                JsonObject o = new JsonObject();
                o.add("players", global(() -> plugin.snapshots().players()));
                return o;
            }
            case "GET /api/v1/console" -> {
                require(caller, Role.MODERATOR);
                return consoleSince(longParam(ex, "since"));
            }
            case "GET /api/v1/chat" -> {
                return chatSince(longParam(ex, "since"));
            }
            case "POST /api/v1/chat" -> {
                require(caller, Role.MODERATOR);
                return broadcast(caller, stringField(readBody(ex), "message", 256));
            }
            case "GET /api/v1/commands" -> {
                require(caller, Role.ADMIN);
                List<String> blocked = plugin.getConfig().getStringList("security.blocked-commands");
                return global(() -> Commands.list(blocked));
            }
            case "GET /api/v1/complete" -> {
                require(caller, Role.ADMIN);
                String buffer = stringParam(ex, "buffer");
                if (buffer.startsWith("/")) {
                    buffer = buffer.substring(1);
                }
                if (buffer.length() > 512) {
                    throw new ApiException(413, "too_large", "Buffer too long");
                }
                String finalBuffer = buffer;
                List<String> blocked = plugin.getConfig().getStringList("security.blocked-commands");
                return global(() -> Commands.complete(finalBuffer, blocked));
            }
            case "GET /api/v1/whitelist" -> {
                require(caller, Role.MODERATOR);
                return global(Moderation::whitelist);
            }
            case "POST /api/v1/whitelist" -> {
                require(caller, Role.ADMIN);
                JsonObject body = readBody(ex);
                if (!body.has("enabled") || !body.get("enabled").isJsonPrimitive()) {
                    throw ApiException.badRequest("bad_request", "Missing field: enabled");
                }
                boolean on = body.get("enabled").getAsBoolean();
                global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:whitelist " + (on ? "on" : "off")));
                plugin.audit().log(caller.who(), caller.ip(), "whitelist " + (on ? "on" : "off"));
                return global(Moderation::whitelist);
            }
            case "POST /api/v1/whitelist/add", "POST /api/v1/whitelist/remove" -> {
                require(caller, Role.MODERATOR);
                String name = playerName(stringField(readBody(ex), "name", 16));
                boolean add = path.endsWith("/add");
                // The vanilla command also looks up players who never joined.
                boolean listed = global(() -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:whitelist " + (add ? "add " : "remove ") + name);
                    return Moderation.isWhitelisted(name);
                });
                if (listed != add) {
                    throw ApiException.notFound("player_unknown", "No Minecraft account named " + name);
                }
                plugin.audit().log(caller.who(), caller.ip(), "whitelist " + (add ? "add " : "remove ") + name);
                return global(Moderation::whitelist);
            }
            case "GET /api/v1/plugins" -> {
                require(caller, Role.MODERATOR);
                return global(ApiServer::plugins);
            }
            case "GET /api/v1/bans" -> {
                require(caller, Role.MODERATOR);
                return global(Moderation::bans);
            }
            case "POST /api/v1/command" -> {
                require(caller, Role.ADMIN);
                return runCommand(caller, stringField(readBody(ex), "command", 1024));
            }
            default -> {
                if (parts.length >= 5 && parts[3].equals("worlds")) {
                    return worldAction(caller, method, java.net.URLDecoder.decode(parts[4], java.nio.charset.StandardCharsets.UTF_8),
                            parts.length > 5 ? parts[5] : "", ex);
                }
                if (method.equals("POST") && parts.length == 6 && parts[3].equals("bans") && parts[5].equals("pardon")) {
                    require(caller, Role.MODERATOR);
                    String name = playerName(parts[4]);
                    boolean stillBanned = global(() -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:pardon " + name);
                        return Moderation.isBanned(name);
                    });
                    if (stillBanned) {
                        throw new ApiException(500, "action_failed", "Could not unban " + name);
                    }
                    plugin.audit().log(caller.who(), caller.ip(), "pardon " + name);
                    return global(Moderation::bans);
                }
                if (method.equals("POST") && parts.length == 6 && parts[3].equals("players")) {
                    return playerAction(caller, parts[4], parts[5], readBody(ex));
                }
                throw ApiException.notFound("not_found", "Unknown endpoint: " + method + " " + path);
            }
        }
    }

    // ── Auth & pairing ──────────────────────────────────────────────────

    private Caller authenticate(HttpExchange ex, String ip) throws ApiException {
        if (plugin.loginGuard().isLocked(ip)) {
            throw new ApiException(429, "locked_out", "Too many failed attempts. Try again later.");
        }
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String presented = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        TokenStore.Token token = plugin.tokens().authenticate(presented);
        if (token == null) {
            if (plugin.loginGuard().fail(ip)) {
                plugin.getLogger().warning("Locked out " + ip + " after too many invalid tokens");
                plugin.audit().log("-", ip, "lockout (invalid tokens)");
            }
            throw new ApiException(401, "invalid_token", "Invalid or revoked token");
        }
        plugin.loginGuard().success(ip);
        return new Caller(token.name(), token.role(), ip);
    }

    private static void require(Caller caller, Role role) throws ApiException {
        if (!caller.role().atLeast(role)) {
            throw ApiException.forbidden("This device needs the " + role.id() + " role");
        }
    }

    private JsonObject ping() {
        JsonObject o = new JsonObject();
        o.addProperty("app", "RemoteMC");
        o.addProperty("version", plugin.getDescription().getVersion());
        o.addProperty("apiVersion", API_VERSION);
        o.addProperty("platform", Platform.name());
        return o;
    }

    private JsonObject pair(JsonObject body, String ip) throws Exception {
        if (plugin.loginGuard().isLocked(ip)) {
            throw new ApiException(429, "locked_out", "Too many failed attempts. Try again later.");
        }
        PairingManager.Code code = plugin.pairing().redeem(stringField(body, "code", 16));
        if (code == null) {
            plugin.loginGuard().fail(ip);
            throw new ApiException(401, "invalid_code", "Pairing code is wrong or expired");
        }
        plugin.loginGuard().success(ip);
        String device = body.has("device") && body.get("device").isJsonPrimitive()
                ? body.get("device").getAsString() : "Device";
        String token = plugin.tokens().create(device.replaceAll("[\\p{Cntrl}]", ""), code.role());
        TokenStore.Token created = plugin.tokens().authenticate(token);
        plugin.audit().log(created.name() + "/" + created.role().id(), ip, "paired (code from " + code.createdBy() + ")");
        plugin.onDevicePaired(created.name(), created.role());

        JsonObject o = new JsonObject();
        o.addProperty("token", token);
        o.addProperty("name", created.name());
        o.addProperty("role", created.role().id());
        return o;
    }

    private JsonObject me(Caller caller) {
        JsonObject o = new JsonObject();
        o.addProperty("name", caller.name());
        o.addProperty("role", caller.role().id());
        o.addProperty("commandsEnabled", plugin.getConfig().getBoolean("security.allow-commands", true));
        o.addProperty("capturesCommandOutput", Platform.capturesCommandOutput());
        o.addProperty("apiVersion", API_VERSION);
        return o;
    }

    // ── Live stream ─────────────────────────────────────────────────────

    private void stream(HttpExchange ex, Caller caller) throws Exception {
        if (openStreams.incrementAndGet() > MAX_STREAMS) {
            openStreams.decrementAndGet();
            throw new ApiException(503, "too_many_streams", "Too many live connections");
        }
        try (EventHub.Subscription sub = plugin.hub().subscribe(caller.role())) {
            JsonObject hello = me(caller);
            hello.add("snapshot", global(() -> plugin.snapshots().full()));
            hello.addProperty("consoleLast", plugin.console().lastId());
            hello.addProperty("chatLast", plugin.chat().lastId());

            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("X-Accel-Buffering", "no"); // nginx: don't buffer
            ex.sendResponseHeaders(200, 0);
            OutputStream out = ex.getResponseBody();
            write(out, "retry: 3000\n\n" + EventHub.frame("hello", hello));

            while (!sub.overflowed()) {
                String frame = sub.next(HEARTBEAT_MILLIS);
                write(out, frame != null ? frame : ": ping\n\n");
            }
        } catch (IOException e) {
            // client went away
        } finally {
            openStreams.decrementAndGet();
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // ── Endpoints ───────────────────────────────────────────────────────

    private JsonObject consoleSince(long since) {
        JsonArray arr = new JsonArray();
        for (ConsoleBuffer.Line line : plugin.console().since(since)) {
            arr.add(line.toJson());
        }
        JsonObject o = new JsonObject();
        o.add("lines", arr);
        o.addProperty("last", plugin.console().lastId());
        return o;
    }

    private JsonObject chatSince(long since) {
        JsonArray arr = new JsonArray();
        for (ChatBuffer.Entry e : plugin.chat().since(since)) {
            arr.add(e.toJson());
        }
        JsonObject o = new JsonObject();
        o.add("messages", arr);
        o.addProperty("last", plugin.chat().lastId());
        return o;
    }

    @SuppressWarnings("deprecation")
    private JsonObject broadcast(Caller caller, String message) throws Exception {
        global(() -> {
            Bukkit.broadcastMessage("§8[§aServer§8]§r " + message);
            return null;
        });
        plugin.chat().add(ChatBuffer.Type.BROADCAST, caller.name(), message);
        plugin.audit().log(caller.who(), caller.ip(), "broadcast: " + message);
        return ok();
    }

    private JsonObject runCommand(Caller caller, String command) throws Exception {
        if (!plugin.getConfig().getBoolean("security.allow-commands", true)) {
            throw ApiException.forbidden("Console commands are disabled in config.yml");
        }
        String cmd = command.strip();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        if (cmd.isBlank()) {
            throw ApiException.badRequest("bad_request", "Empty command");
        }
        String label = cmd.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        for (String blocked : plugin.getConfig().getStringList("security.blocked-commands")) {
            if (blocked.equalsIgnoreCase(label)) {
                throw new ApiException(403, "command_blocked", "The command '" + label + "' is blocked in config.yml");
            }
        }

        String finalCmd = cmd;
        List<String> output = new ArrayList<>();
        plugin.audit().log(caller.who(), caller.ip(), "command: /" + finalCmd);
        plugin.getLogger().info(caller.name() + " ran: /" + finalCmd);
        boolean success = global(() -> {
            CommandSender sender = Platform.commandSender(output);
            return Bukkit.dispatchCommand(sender, finalCmd);
        });

        if (Platform.capturesCommandOutput()) {
            awaitLateOutput(output);
        }

        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        o.addProperty("captured", Platform.capturesCommandOutput());
        JsonArray plain = new JsonArray();
        JsonArray formatted = new JsonArray();
        synchronized (output) {
            for (String line : output) {
                plain.add(Formatting.strip(line));
                formatted.add(line);
            }
        }
        o.add("output", plain);
        o.add("formatted", formatted);
        return o;
    }

    /**
     * Some commands answer asynchronously (Folia runs world commands on region threads,
     * others fetch data first). Wait briefly for the first line, then for stragglers.
     */
    private static void awaitLateOutput(List<String> output) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1500;
        int seen = -1;
        while (System.currentTimeMillis() < deadline) {
            int size;
            synchronized (output) {
                size = output.size();
            }
            if (size > 0 && size == seen) {
                return; // nothing new during the last interval
            }
            seen = size;
            Thread.sleep(size > 0 ? 150 : 50);
        }
    }

    @SuppressWarnings("deprecation")
    private JsonObject playerAction(Caller caller, String name, String action, JsonObject body) throws Exception {
        if (!PLAYER_NAME.matcher(name).matches()) {
            throw ApiException.badRequest("bad_request", "Invalid player name");
        }
        String reason = body.has("reason") && !body.get("reason").isJsonNull()
                ? body.get("reason").getAsString().replaceAll("[\\p{Cntrl}]", " ").strip() : "";
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        String finalReason = reason;

        require(caller, action.equals("op") || action.equals("deop") ? Role.ADMIN : Role.MODERATOR);

        if (action.equals("ban")) {
            // The vanilla command also resolves offline players.
            boolean ok = global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "minecraft:ban " + name + (finalReason.isEmpty() ? "" : " " + finalReason)));
            if (!ok) {
                throw new ApiException(500, "action_failed", "Ban failed");
            }
        } else {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                throw ApiException.notFound("player_offline", "Player " + name + " is not online");
            }
            switch (action) {
                case "kick" -> onPlayer(p, () -> p.kickPlayer(finalReason.isEmpty() ? "Kicked from the server" : finalReason));
                case "op" -> global(() -> { p.setOp(true); return null; });
                case "deop" -> global(() -> { p.setOp(false); return null; });
                case "heal" -> onPlayer(p, () -> {
                    p.setHealth(Platform.maxHealth(p));
                    p.setFoodLevel(20);
                    p.setSaturation(20f);
                    p.setFireTicks(0);
                });
                case "gamemode" -> {
                    GameMode mode;
                    try {
                        mode = GameMode.valueOf(stringField(body, "mode", 16).toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        throw ApiException.badRequest("bad_request", "Unknown game mode");
                    }
                    onPlayer(p, () -> p.setGameMode(mode));
                }
                case "message" -> {
                    String message = stringField(body, "message", 256);
                    onPlayer(p, () -> p.sendMessage("§8[§aServer§8]§r " + message));
                }
                default -> throw ApiException.notFound("not_found", "Unknown action: " + action);
            }
        }
        plugin.audit().log(caller.who(), caller.ip(), action + " " + name + (finalReason.isEmpty() ? "" : " (" + finalReason + ")"));
        return ok();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** GET /worlds/<name>; POST /worlds/<name>/time|weather|difficulty|gamerule (admin). */
    private JsonObject worldAction(Caller caller, String method, String world, String action, HttpExchange ex) throws Exception {
        require(caller, Role.ADMIN);
        if (method.equals("GET") && action.isEmpty()) {
            return global(() -> safeDescribe(world));
        }
        if (!method.equals("POST")) {
            throw ApiException.notFound("not_found", "Unknown endpoint");
        }
        JsonObject body = readBody(ex);
        String what = switch (action) {
            case "time" -> "time " + stringField(body, "time", 16);
            case "weather" -> "weather " + stringField(body, "weather", 16);
            case "difficulty" -> "difficulty " + stringField(body, "difficulty", 16);
            case "gamerule" -> "gamerule " + stringField(body, "rule", 64) + " " + body.get("value");
            default -> throw ApiException.notFound("not_found", "Unknown world action: " + action);
        };
        JsonObject result = global(() -> {
            try {
                World w = WorldControl.world(world);
                switch (action) {
                    case "time" -> WorldControl.setTime(w, body.get("time").getAsString());
                    case "weather" -> WorldControl.setWeather(w, body.get("weather").getAsString());
                    case "difficulty" -> WorldControl.setDifficulty(w, body.get("difficulty").getAsString());
                    default -> WorldControl.setGameRule(w, body.get("rule").getAsString(), body.get("value"));
                }
                return WorldControl.describe(w);
            } catch (ApiException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
        plugin.audit().log(caller.who(), caller.ip(), world + ": " + what);
        return result;
    }

    private static JsonObject safeDescribe(String world) {
        try {
            return WorldControl.describe(WorldControl.world(world));
        } catch (ApiException e) {
            throw new java.util.concurrent.CompletionException(e);
        }
    }

    @SuppressWarnings("deprecation") // getDescription works on Paper and Spigot alike
    private static JsonObject plugins() {
        JsonArray arr = new JsonArray();
        org.bukkit.plugin.Plugin[] all = Bukkit.getPluginManager().getPlugins();
        java.util.Arrays.sort(all, java.util.Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        for (org.bukkit.plugin.Plugin p : all) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName());
            o.addProperty("version", p.getDescription().getVersion());
            o.addProperty("enabled", p.isEnabled());
            if (p.getDescription().getDescription() != null) {
                o.addProperty("description", p.getDescription().getDescription());
            }
            JsonArray authors = new JsonArray();
            p.getDescription().getAuthors().forEach(authors::add);
            o.add("authors", authors);
            if (p.getDescription().getWebsite() != null) {
                o.addProperty("website", p.getDescription().getWebsite());
            }
            arr.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("plugins", arr);
        return o;
    }

    private static String playerName(String name) throws ApiException {
        if (!PLAYER_NAME.matcher(name).matches()) {
            throw ApiException.badRequest("bad_request", "Invalid player name");
        }
        return name;
    }

    private <T> T global(Supplier<T> task) throws Exception {
        return await(plugin.scheduler().global(task));
    }

    private void onPlayer(Player p, Runnable task) throws Exception {
        await(plugin.scheduler().entity(p, () -> {
            task.run();
            return null;
        }));
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // Tasks wrap checked API errors in CompletionException to pass them out.
            if (cause instanceof java.util.concurrent.CompletionException ce && ce.getCause() instanceof ApiException api) {
                throw api;
            }
            throw cause instanceof Exception c ? c : e;
        } catch (TimeoutException e) {
            throw new ApiException(504, "timeout", "The server did not respond in time");
        }
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static String stringParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv[0].equals(key)) {
                    return kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                }
            }
        }
        return "";
    }

    private static String param(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv[0].equals(key) && kv.length > 1) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private static long longParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv[0].equals(key) && kv.length > 1) {
                    try {
                        return Long.parseLong(kv[1]);
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    private static JsonObject readBody(HttpExchange ex) throws IOException, ApiException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) {
                throw new ApiException(413, "too_large", "Request too large");
            }
            if (data.length == 0) {
                return new JsonObject();
            }
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw ApiException.badRequest("invalid_json", "Invalid JSON");
        }
    }

    private static String stringField(JsonObject body, String key, int maxLength) throws ApiException {
        if (!body.has(key) || body.get(key).isJsonNull() || !body.get(key).isJsonPrimitive()) {
            throw ApiException.badRequest("bad_request", "Missing field '" + key + "'");
        }
        String value = body.get(key).getAsString();
        if (value.isBlank()) {
            throw ApiException.badRequest("bad_request", "Field '" + key + "' is empty");
        }
        if (value.length() > maxLength) {
            throw new ApiException(413, "too_large", "Field '" + key + "' is too long");
        }
        return value;
    }

    private static void sendError(HttpExchange ex, int status, String code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", code);
        o.addProperty("message", message);
        try {
            send(ex, status, o);
        } catch (IOException ignored) {
            // response already started or client gone
        }
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
