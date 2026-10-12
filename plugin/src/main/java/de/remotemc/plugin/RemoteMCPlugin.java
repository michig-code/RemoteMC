package de.remotemc.plugin;

import com.google.zxing.common.BitMatrix;
import de.remotemc.plugin.api.ApiServer;
import de.remotemc.plugin.auth.LoginGuard;
import de.remotemc.plugin.auth.PairingManager;
import de.remotemc.plugin.auth.Role;
import de.remotemc.plugin.auth.TokenStore;
import de.remotemc.plugin.live.ChatBuffer;
import de.remotemc.plugin.live.ConsoleBuffer;
import de.remotemc.plugin.live.EventHub;
import de.remotemc.plugin.live.GameListener;
import de.remotemc.plugin.live.PaperChatListener;
import de.remotemc.plugin.live.Snapshots;
import de.remotemc.plugin.live.SpigotChatListener;
import de.remotemc.plugin.pairing.QrCodes;
import de.remotemc.plugin.platform.Platform;
import de.remotemc.plugin.platform.Scheduler;
import de.remotemc.plugin.tls.TlsManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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

public final class RemoteMCPlugin extends JavaPlugin implements Listener {

    public static final String PERMISSION = "remotemc.admin";

    private Messages messages;
    private TokenStore tokens;
    private final PairingManager pairing = new PairingManager();
    private LoginGuard loginGuard;
    private AuditLog audit;
    private final EventHub hub = new EventHub();
    private ConsoleBuffer console;
    private ChatBuffer chat;
    private Snapshots snapshots;
    private Scheduler scheduler;
    private TlsManager tls;
    private ApiServer api;
    private Runnable statusTask;
    private ScheduledExecutorService housekeeping;
    private volatile UpdateChecker.Update update;

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        importFromMcRemote();
        saveDefaultConfig();
        // Adds settings introduced in newer versions while keeping the user's values.
        getConfig().options().copyDefaults(true);

        scheduler = Scheduler.create(this);
        tokens = new TokenStore(new File(getDataFolder(), "tokens.yml"), getLogger());
        tokens.load();
        String legacy = getConfig().getString("api.token", "");
        if (legacy != null && !legacy.isBlank()) {
            tokens.importLegacy(legacy);
            getConfig().set("api.token", null);
            getLogger().info("Moved the old access token to tokens.yml (name: Legacy token).");
        }
        saveConfig();

        messages = new Messages(this, getConfig().getString("language", "en"));
        loginGuard = new LoginGuard(getConfig().getInt("security.max-failed-attempts", 5),
                getConfig().getLong("security.lockout-seconds", 300));
        audit = new AuditLog(new File(getDataFolder(), "audit.log"), getLogger(), getConfig().getBoolean("audit-log", true));
        snapshots = new Snapshots(getDescription().getVersion());
        console = new ConsoleBuffer(hub, getConfig().getInt("console.buffer-size", 500));
        console.attach();
        chat = new ChatBuffer(hub);

        getServer().getPluginManager().registerEvents(new GameListener(chat), this);
        getServer().getPluginManager().registerEvents(
                Platform.PAPER ? new PaperChatListener(chat) : new SpigotChatListener(chat), this);
        getServer().getPluginManager().registerEvents(this, this);

        startApi();
        // Live status for connected apps every 2 seconds.
        statusTask = scheduler.repeat(() -> {
            if (hub.hasSubscribers()) {
                hub.publish("status", snapshots.full(), Role.VIEWER);
            }
        }, 40L);

        housekeeping = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RemoteMC-Housekeeping");
            t.setDaemon(true);
            return t;
        });
        housekeeping.scheduleAtFixedRate(tokens::saveIfDirty, 5, 5, TimeUnit.MINUTES);
        if (getConfig().getBoolean("update-check", true)) {
            housekeeping.execute(() -> {
                update = UpdateChecker.check(getDescription().getVersion());
                if (update != null) {
                    getLogger().info("RemoteMC " + update.version() + " is available: " + update.url());
                }
            });
        }
        getLogger().info("Running on " + Platform.name() + ". Pair the app with /remotemc pair");
    }

    @Override
    public void onDisable() {
        if (statusTask != null) {
            statusTask.run();
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

    /** RemoteMC 1.x was called McRemote; take over its settings and token on first start. */
    private void importFromMcRemote() {
        File old = new File(getDataFolder().getParentFile(), "McRemote/config.yml");
        if (old.isFile() && !getDataFolder().exists()) {
            try {
                getDataFolder().mkdirs();
                java.nio.file.Files.copy(old.toPath(), new File(getDataFolder(), "config.yml").toPath());
                getLogger().info("Imported settings from McRemote. You can delete McRemote's jar and folder now.");
            } catch (java.io.IOException e) {
                getLogger().warning("Could not import McRemote settings: " + e.getMessage());
            }
        }
    }

    private void startApi() {
        try {
            tls = null;
            if (getConfig().getBoolean("api.tls", true)) {
                tls = new TlsManager(new File(getDataFolder(), "tls.p12"), getLogger());
                tls.load();
            }
            api = new ApiServer(this);
            api.start();
            getLogger().info("API listening on port " + getConfig().getInt("api.port")
                    + (tls != null ? " (HTTPS, fingerprint " + tls.shortFingerprint() + ")" : " (HTTP, unencrypted)"));
        } catch (Exception e) {
            getLogger().severe("Could not start the RemoteMC API: " + e.getMessage()
                    + " – is port " + getConfig().getInt("api.port") + " already in use?");
            api = null;
        }
    }

    private void stopApi() {
        if (api != null) {
            api.stop();
            api = null;
        }
    }

    // ── Accessors for the API ───────────────────────────────────────────

    public TokenStore tokens() { return tokens; }
    public PairingManager pairing() { return pairing; }
    public LoginGuard loginGuard() { return loginGuard; }
    public AuditLog audit() { return audit; }
    public EventHub hub() { return hub; }
    public ConsoleBuffer console() { return console; }
    public ChatBuffer chat() { return chat; }
    public Snapshots snapshots() { return snapshots; }
    public Scheduler scheduler() { return scheduler; }
    public TlsManager tls() { return tls; }

    /** Tells admins that a device was paired. */
    public void onDevicePaired(String device, Role role) {
        String msg = messages.prefixed("pair-success", "device", device, "role", role.id());
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(PERMISSION)) {
                p.sendMessage(msg);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UpdateChecker.Update u = update;
        if (u != null && event.getPlayer().hasPermission(PERMISSION)) {
            event.getPlayer().sendMessage(messages.prefixed("update-available",
                    "version", u.version(), "current", getDescription().getVersion(), "url", u.url()));
        }
    }

    // ── Commands ────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.prefixed("no-permission"));
            return true;
        }
        String sub = args.length == 0 ? "pair" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pair" -> pair(sender, args.length > 1 ? args[1] : "admin");
            case "info" -> info(sender);
            case "token", "tokens" -> token(sender, Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
            case "reload" -> reload(sender);
            default -> sender.sendMessage(messages.prefixed("usage"));
        }
        return true;
    }

    private void pair(CommandSender sender, String roleArg) {
        Role role = Role.parse(roleArg);
        if (role == null) {
            sender.sendMessage(messages.prefixed("role-invalid"));
            return;
        }
        if (api == null) {
            sender.sendMessage(messages.prefixed("api-stopped"));
            return;
        }
        int minutes = Math.max(1, getConfig().getInt("security.pairing-code-minutes", 5));
        PairingManager.Code code = pairing.create(role, minutes, sender.getName());
        String address = publicAddress();
        int port = getConfig().getInt("api.port");
        String link = "remotemc://" + hostForUri(address) + ":" + port + "?code=" + code.code()
                + (tls != null ? "&fp=" + tls.fingerprintToken() : "&tls=0");
        BitMatrix qr = QrCodes.encode(link);

        sender.sendMessage(messages.prefixed("pair-header", "code", code.code(), "minutes", minutes, "role", role.id()));
        if (sender instanceof Player player) {
            String mapName = messages.get("pair-map-name", "code", code.code());
            scheduler.entity(player, () -> {
                ItemStack map = QrCodes.mapItem(player, qr, mapName);
                player.getInventory().addItem(map).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
                return null;
            });
            sender.sendMessage(messages.get("pair-scan"));
        } else if (QrCodes.consoleSupportsBlocks()) {
            sender.sendMessage(messages.get("pair-scan-console"));
            QrCodes.toText(qr).forEach(sender::sendMessage);
        }
        sender.sendMessage(messages.get("pair-manual", "address", address, "port", port, "code", code.code()));
        if (tls != null) {
            sender.sendMessage(messages.get("pair-verify", "short", tls.shortFingerprint()));
        }
        TextComponent linkLine = new TextComponent(messages.get("pair-link"));
        TextComponent clickable = new TextComponent(link);
        clickable.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, link));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(messages.get("pair-link-hover"))));
        linkLine.addExtra(clickable);
        sender.spigot().sendMessage(linkLine);
    }

    private void info(CommandSender sender) {
        if (api != null) {
            sender.sendMessage(messages.prefixed("api-running", "port", getConfig().getInt("api.port"),
                    "tls", messages.get(tls != null ? "tls-on" : "tls-off")));
            if (tls != null) {
                sender.sendMessage(messages.get("fingerprint", "fp", tls.shortFingerprint()));
            }
        } else {
            sender.sendMessage(messages.prefixed("api-stopped"));
        }
        sender.sendMessage(messages.get("info-tokens", "count", tokens.size()));
        sender.sendMessage(messages.get("info-clients", "count", hub.subscriberCount()));
    }

    private void token(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(messages.prefixed("usage"));
                    return;
                }
                Role role = Role.parse(args.length > 2 ? args[2] : "admin");
                if (role == null) {
                    sender.sendMessage(messages.prefixed("role-invalid"));
                    return;
                }
                if (tokens.nameExists(args[1])) {
                    sender.sendMessage(messages.prefixed("token-exists", "name", args[1]));
                    return;
                }
                String token = tokens.create(args[1], role);
                audit.log(sender.getName(), "-", "created token " + args[1] + " (" + role.id() + ")");
                sender.sendMessage(messages.prefixed("token-created", "name", args[1], "role", role.id()));
                TextComponent value = new TextComponent(token + " ");
                value.setColor(net.md_5.bungee.api.ChatColor.WHITE);
                value.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, token));
                value.addExtra(new TextComponent(messages.get("token-copy")));
                sender.spigot().sendMessage(value);
            }
            case "list" -> {
                if (tokens.size() == 0) {
                    sender.sendMessage(messages.prefixed("token-list-empty"));
                    return;
                }
                sender.sendMessage(messages.prefixed("token-list-header"));
                DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                for (TokenStore.Token t : tokens.all()) {
                    sender.sendMessage(messages.get("token-list-entry", "name", t.name(), "role", t.role().id(),
                            "used", t.lastUsed() == 0 ? messages.get("never") : format.format(new Date(t.lastUsed()))));
                }
            }
            case "revoke", "delete", "remove" -> {
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (tokens.revoke(name)) {
                    audit.log(sender.getName(), "-", "revoked token " + name);
                    sender.sendMessage(messages.prefixed("token-revoked", "name", name));
                } else {
                    sender.sendMessage(messages.prefixed("token-not-found", "name", name));
                }
            }
            default -> sender.sendMessage(messages.prefixed("usage"));
        }
    }

    private void reload(CommandSender sender) {
        stopApi();
        reloadConfig();
        messages = new Messages(this, getConfig().getString("language", "en"));
        loginGuard.configure(getConfig().getInt("security.max-failed-attempts", 5), getConfig().getLong("security.lockout-seconds", 300));
        audit.setEnabled(getConfig().getBoolean("audit-log", true));
        console.resize(getConfig().getInt("console.buffer-size", 500));
        tokens.load();
        startApi();
        sender.sendMessage(messages.prefixed("reloaded"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
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
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Address for QR codes: configured address, server-ip, or the machine's LAN address. */
    String publicAddress() {
        String configured = getConfig().getString("api.public-address", "");
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        String ip = Bukkit.getIp();
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
