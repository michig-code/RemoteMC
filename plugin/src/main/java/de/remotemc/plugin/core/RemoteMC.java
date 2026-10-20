package de.remotemc.plugin.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.zxing.common.BitMatrix;
import de.remotemc.plugin.AuditLog;
import de.remotemc.plugin.Messages;
import de.remotemc.plugin.UpdateChecker;
import de.remotemc.plugin.api.ApiServer;
import de.remotemc.plugin.auth.LoginGuard;
import de.remotemc.plugin.auth.PairingManager;
import de.remotemc.plugin.auth.Role;
import de.remotemc.plugin.auth.TokenStore;
import de.remotemc.plugin.live.ChatBuffer;
import de.remotemc.plugin.live.ConsoleBuffer;
import de.remotemc.plugin.live.EventHub;
import de.remotemc.plugin.live.Formatting;
import de.remotemc.plugin.live.History;
import de.remotemc.plugin.live.Schedules;
import de.remotemc.plugin.pairing.QrText;
import de.remotemc.plugin.tls.TlsManager;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The heart of RemoteMC, the same on Paper/Spigot/Folia and Fabric: the API server, pairing,
 * tokens, console and chat buffers, history, scheduled tasks and the /remotemc command. The
 * server software only supplies a {@link Game}, the {@link Settings} and the {@link Sender}s.
 */
public final class RemoteMC {

    /** Permission (Bukkit) for /remotemc; on Fabric operators (level 4) may use it. */
    public static final String PERMISSION = "remotemc.admin";

    /** What this version can do, so apps hide what an older plugin or mod lacks. */
    public static final String[] FEATURES = {"history", "moderation", "worlds", "plugins", "schedules", "teleport", "files", "inventory"};

    private static final Pattern TOKEN = Pattern.compile("rmc_[A-Za-z0-9_-]{20,}");
    private static final Pattern QR_LINE = Pattern.compile("^[█▀▄ ]{10,}$");

    private final File dataFolder;
    private final Logger logger;
    private final String version;
    private final Settings config;
    private final Game game;
    private final long startedAt = System.currentTimeMillis();

    private Messages messages;
    private TokenStore tokens;
    private final PairingManager pairing = new PairingManager();
    private LoginGuard loginGuard;
    private AuditLog audit;
    private final EventHub hub = new EventHub();
    private ConsoleBuffer console;
    private ChatBuffer chat;
    private TlsManager tls;
    private ApiServer api;
    private Runnable statusTask;
    private Runnable historyTask;
    private History history;
    private Schedules schedules;
    private ScheduledExecutorService housekeeping;
    private volatile UpdateChecker.Update update;

    public RemoteMC(File dataFolder, Logger logger, String version, Settings config, Game game) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.version = version;
        this.config = config;
        this.game = game;
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    public void start() {
        dataFolder.mkdirs();
        tokens = new TokenStore(new File(dataFolder, "tokens.yml"), logger);
        tokens.load();
        messages = new Messages(config.getString("language", "en"));
        loginGuard = new LoginGuard(config.getInt("security.max-failed-attempts", 5), config.getLong("security.lockout-seconds", 300));
        audit = new AuditLog(new File(dataFolder, "audit.log"), logger, config.getBoolean("audit-log", true));
        console = new ConsoleBuffer(hub, config.getInt("console.buffer-size", 500), this::redact);
        console.attach();
        chat = new ChatBuffer(hub);

        startApi();
        // Live status for connected apps every 2 seconds.
        statusTask = game.repeat(() -> {
            if (hub.hasSubscribers()) {
                hub.publish("status", snapshotNow(), Role.VIEWER);
            }
        }, 40L);

        housekeeping = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RemoteMC-Housekeeping");
            t.setDaemon(true);
            return t;
        });
        housekeeping.scheduleAtFixedRate(tokens::saveIfDirty, 5, 5, TimeUnit.MINUTES);
        // Scheduled commands: checked every 5 seconds, run on the server thread.
        schedules = new Schedules(new File(dataFolder, "schedules.json"), logger, java.time.ZoneId.systemDefault());
        housekeeping.scheduleAtFixedRate(this::runSchedules, 5, 5, TimeUnit.SECONDS);
        // Performance history: measure on the server thread every minute, save off it.
        if (config.getBoolean("history", true)) {
            ScheduledExecutorService saver = housekeeping;
            history = new History(new File(dataFolder, "history.csv"), logger);
            historyTask = game.repeat(() -> {
                double[] tps = game.tpsNow();
                Runtime rt = Runtime.getRuntime();
                History.Sample sample = new History.Sample(System.currentTimeMillis(),
                        tps.length == 0 ? -1 : Math.min(20.0, tps[0]), game.msptNow(),
                        (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024, game.onlineNow());
                saver.execute(() -> history.add(sample));
            }, 1200L);
        }
        if (config.getBoolean("update-check", true)) {
            housekeeping.execute(() -> {
                update = UpdateChecker.check(version);
                if (update != null) {
                    logger.info("RemoteMC " + update.version() + " is available: " + update.url());
                }
            });
        }
        logger.info("Running on " + game.platformName() + ". Pair the app with /remotemc pair");
    }

    public void stop() {
        if (statusTask != null) {
            statusTask.run();
        }
        if (historyTask != null) {
            historyTask.run();
        }
        stopApi();
        if (console != null) {
            console.detach();
        }
        if (tokens != null) {
            tokens.saveIfDirty();
        }
        if (housekeeping != null) {
            housekeeping.shutdownNow();
        }
        if (audit != null) {
            audit.close();
        }
    }

    private void runSchedules() {
        try {
            schedules.tick(System.currentTimeMillis(),
                    (task, seconds) -> {
                        try {
                            game.broadcast(messages.prefixed("schedule-warning", "task", task.name,
                                    "time", seconds >= 60 ? (seconds / 60) + " min" : seconds + " s"));
                        } catch (Exception e) {
                            logger.warning("Schedule warning failed: " + e);
                        }
                    },
                    task -> {
                        if (!config.getBoolean("security.allow-commands", true)) {
                            return;
                        }
                        // The block list may have changed since the task was saved.
                        try {
                            ApiServer.checkCommand(task.command, config.getStringList("security.blocked-commands"));
                        } catch (Exception e) {
                            logger.warning("Scheduled task '" + task.name + "' skipped: " + e.getMessage());
                            return;
                        }
                        audit.log("schedule " + task.name, "-", "command: /" + task.command);
                        game.runConsoleCommand(task.command);
                    });
        } catch (RuntimeException e) {
            logger.warning("Scheduled task failed: " + e);
        }
    }

    private void startApi() {
        try {
            tls = null;
            if (config.getBoolean("api.tls", true)) {
                tls = new TlsManager(new File(dataFolder, "tls.p12"), logger);
                tls.load();
            }
            api = new ApiServer(this);
            api.start();
            logger.info("API listening on port " + config.getInt("api.port", 8765)
                    + (tls != null ? " (HTTPS, fingerprint " + tls.shortFingerprint() + ")" : " (HTTP, unencrypted)"));
        } catch (Exception e) {
            logger.severe("Could not start the RemoteMC API: " + e.getMessage()
                    + " – is port " + config.getInt("api.port", 8765) + " already in use?");
            api = null;
        }
    }

    private void stopApi() {
        if (api != null) {
            api.stop();
            api = null;
        }
    }

    // ── For the API and the platforms ───────────────────────────────

    public Game game() { return game; }
    public Settings config() { return config; }
    public Logger logger() { return logger; }
    public File dataFolder() { return dataFolder; }
    public String version() { return version; }
    public TokenStore tokens() { return tokens; }
    public PairingManager pairing() { return pairing; }
    public LoginGuard loginGuard() { return loginGuard; }
    public AuditLog audit() { return audit; }
    public EventHub hub() { return hub; }
    public ConsoleBuffer console() { return console; }
    public ChatBuffer chat() { return chat; }
    public Schedules schedules() { return schedules; }
    public Messages messages() { return messages; }

    /** Null when the history is turned off in the config. */
    public History history() { return history; }
    public TlsManager tls() { return tls; }

    /** Seconds since the server started. */
    public long uptimeSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000;
    }

    /** Status and players in one object (live stream). Call on the server thread. */
    public JsonObject snapshotNow() {
        JsonObject o = new JsonObject();
        o.add("status", game.statusNow());
        o.add("players", game.playersNow());
        return o;
    }

    /**
     * The parts of the status JSON that are the same everywhere: plugin version, features, TPS,
     * tick time, memory and uptime. The platform adds MOTD, versions, players and worlds.
     */
    public void addCommonStatus(JsonObject o) {
        o.addProperty("pluginVersion", version);
        JsonArray features = new JsonArray();
        for (String f : FEATURES) {
            features.add(f);
        }
        o.add("features", features);
        JsonArray tps = new JsonArray();
        for (double d : game.tpsNow()) {
            tps.add(Math.min(20.0, Math.round(d * 100.0) / 100.0));
        }
        o.add("tps", tps);
        double mspt = game.msptNow();
        o.addProperty("mspt", mspt < 0 ? -1 : Math.round(mspt * 100.0) / 100.0);
        Runtime rt = Runtime.getRuntime();
        o.addProperty("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        o.addProperty("memoryMaxMb", rt.maxMemory() / 1024 / 1024);
        o.addProperty("uptimeSeconds", uptimeSeconds());
    }

    /** Tells admins that a device was paired; its QR maps disappear. */
    public void onDevicePaired(String device, Role role, String code) {
        game.pairingUsed(code);
        game.notifyAdmins(messages.prefixed("pair-success", "device", device, "role", role.id()));
    }

    /** A player joined: admins hear about a new version. */
    public void onJoin(Sender player) {
        UpdateChecker.Update u = update;
        if (u != null && player.hasPermission()) {
            player.send(messages.prefixed("update-available", "version", u.version(), "current", version, "url", u.url()));
        }
    }

    // ── /remotemc ───────────────────────────────────────────────────

    public void command(Sender sender, String[] args) {
        if (!sender.hasPermission()) {
            sender.send(messages.prefixed("no-permission"));
            return;
        }
        String sub = args.length == 0 ? "pair" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pair" -> pair(sender, args.length > 1 ? args[1] : "admin");
            case "info" -> info(sender);
            case "token", "tokens" -> token(sender, Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
            case "reload" -> reload(sender);
            default -> sender.send(messages.prefixed("usage"));
        }
    }

    private void pair(Sender sender, String roleArg) {
        Role role = Role.parse(roleArg);
        if (role == null) {
            sender.send(messages.prefixed("role-invalid"));
            return;
        }
        if (api == null) {
            sender.send(messages.prefixed("api-stopped"));
            return;
        }
        int minutes = Math.max(1, config.getInt("security.pairing-code-minutes", 5));
        PairingManager.Code code = pairing.create(role, minutes, sender.name());
        String address = publicAddress();
        int port = config.getInt("api.port", 8765);
        String link = "remotemc://" + hostForUri(address) + ":" + port + "?code=" + code.code()
                + (tls != null ? "&fp=" + tls.fingerprintToken() : "&tls=0");
        BitMatrix qr = QrText.encode(link);

        sender.send(messages.prefixed("pair-header", "code", code.code(), "minutes", minutes, "role", role.id()));
        if (sender.isPlayer()) {
            if (sender.giveQrMap(qr, messages.get("pair-map-name", "code", code.code()), code.code())) {
                sender.send(messages.get("pair-scan"));
            }
        } else if (QrText.consoleSupportsBlocks()) {
            sender.send(messages.get("pair-scan-console"));
            QrText.toText(qr).forEach(sender::send);
        }
        sender.send(messages.get("pair-manual", "address", address, "port", port, "code", code.code()));
        if (tls != null) {
            sender.send(messages.get("pair-verify", "short", tls.shortFingerprint()));
        }
        sender.sendLink(messages.get("pair-link"), link, link, false, messages.get("pair-link-hover"));
        if (config.getBoolean("web", false)) {
            // The same code works in the browser (web app of the API server).
            String web = (tls != null ? "https://" : "http://") + hostForUri(address) + ":" + port + "/#code=" + code.code();
            sender.sendLink(messages.get("pair-web"), web, web, true, null);
        }
    }

    private void info(Sender sender) {
        if (api != null) {
            sender.send(messages.prefixed("api-running", "port", config.getInt("api.port", 8765),
                    "tls", messages.get(tls != null ? "tls-on" : "tls-off")));
            if (tls != null) {
                sender.send(messages.get("fingerprint", "fp", tls.shortFingerprint()));
            }
        } else {
            sender.send(messages.prefixed("api-stopped"));
        }
        sender.send(messages.get("info-tokens", "count", tokens.size()));
        sender.send(messages.get("info-clients", "count", hub.subscriberCount()));
    }

    private void token(Sender sender, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    sender.send(messages.prefixed("usage"));
                    return;
                }
                Role role = Role.parse(args.length > 2 ? args[2] : "admin");
                if (role == null) {
                    sender.send(messages.prefixed("role-invalid"));
                    return;
                }
                if (tokens.nameExists(args[1])) {
                    sender.send(messages.prefixed("token-exists", "name", args[1]));
                    return;
                }
                String token = tokens.create(args[1], role);
                audit.log(sender.name(), "-", "created token " + args[1] + " (" + role.id() + ")");
                sender.send(messages.prefixed("token-created", "name", args[1], "role", role.id()));
                sender.sendLink("", token + " ", token, false, null);
                sender.send(messages.get("token-copy"));
            }
            case "list" -> {
                if (tokens.size() == 0) {
                    sender.send(messages.prefixed("token-list-empty"));
                    return;
                }
                sender.send(messages.prefixed("token-list-header"));
                DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                for (TokenStore.Token t : tokens.all()) {
                    sender.send(messages.get("token-list-entry", "name", t.name(), "role", t.role().id(),
                            "used", t.lastUsed() == 0 ? messages.get("never") : format.format(new Date(t.lastUsed()))));
                }
            }
            case "revoke", "delete", "remove" -> {
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (tokens.revoke(name)) {
                    audit.log(sender.name(), "-", "revoked token " + name);
                    sender.send(messages.prefixed("token-revoked", "name", name));
                } else {
                    sender.send(messages.prefixed("token-not-found", "name", name));
                }
            }
            default -> sender.send(messages.prefixed("usage"));
        }
    }

    private void reload(Sender sender) {
        stopApi();
        config.reload();
        messages = new Messages(config.getString("language", "en"));
        loginGuard.configure(config.getInt("security.max-failed-attempts", 5), config.getLong("security.lockout-seconds", 300));
        audit.setEnabled(config.getBoolean("audit-log", true));
        console.resize(config.getInt("console.buffer-size", 500));
        tokens.load();
        startApi();
        sender.send(messages.prefixed("reloaded"));
    }

    /** Suggestions for /remotemc; {@code args} ends with the word being typed. */
    public List<String> tabComplete(String[] args) {
        List<String> options = switch (args.length) {
            case 1 -> List.of("pair", "info", "token", "reload");
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "pair" -> List.of("admin", "moderator", "viewer");
                case "token" -> List.of("create", "list", "revoke");
                default -> List.of();
            };
            case 3 -> args[0].equalsIgnoreCase("token") && args[1].equalsIgnoreCase("revoke")
                    ? tokens.all().stream().map(TokenStore.Token::name).toList() : List.of();
            case 4 -> args[0].equalsIgnoreCase("token") && args[1].equalsIgnoreCase("create")
                    ? List.of("admin", "moderator", "viewer") : List.of();
            default -> List.of();
        };
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Console lines are visible to moderators in the app; access tokens, pairing codes and
     * console QR codes would let them gain admin access, so they are hidden.
     */
    private String redact(String line) {
        String stripped = Formatting.strip(line).strip();
        if (QR_LINE.matcher(stripped).matches()) {
            return "[QR code hidden]";
        }
        return pairing.redact(TOKEN.matcher(line).replaceAll("rmc_[hidden]"));
    }

    /** Address for QR codes: configured address, server-ip, or the machine's LAN address. */
    public String publicAddress() {
        String configured = config.getString("api.public-address", "");
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        String ip = game.serverIp();
        if (ip != null && !ip.isBlank() && !ip.equals("0.0.0.0")) {
            return ip;
        }
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "localhost";
    }

    private static String hostForUri(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }
}
