package de.remotemc.plugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.remotemc.plugin.core.Game;
import de.remotemc.plugin.core.RemoteMC;
import de.remotemc.plugin.core.Tasks;
import de.remotemc.plugin.live.Snapshots;
import de.remotemc.plugin.platform.Platform;
import de.remotemc.plugin.platform.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** RemoteMC on Paper, Folia and Spigot. */
public final class BukkitGame implements Game {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final Consumer<String> onPairingUsed;
    private Snapshots snapshots;

    public BukkitGame(Plugin plugin, Scheduler scheduler, Consumer<String> onPairingUsed) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.onPairingUsed = onPairingUsed;
    }

    /** Called once the shared core exists (the status JSON needs it). */
    public void bind(RemoteMC core) {
        snapshots = new Snapshots(core);
    }

    @Override
    public String platformName() {
        return Platform.name();
    }

    @Override
    public boolean capturesCommandOutput() {
        return Platform.capturesCommandOutput();
    }

    @Override
    public Path serverFolder() {
        // plugins/RemoteMC → the server folder
        return plugin.getDataFolder().getAbsoluteFile().getParentFile().getParentFile().toPath();
    }

    @Override
    public Path ownJar() {
        return jarOf(plugin);
    }

    @Override
    public String serverIp() {
        return Bukkit.getIp();
    }

    @Override
    public <T> CompletableFuture<T> global(Supplier<T> task) {
        return scheduler.global(task);
    }

    @Override
    public Runnable repeat(Runnable task, long ticks) {
        return scheduler.repeat(task, ticks);
    }

    // ── Status ──────────────────────────────────────────────────────

    @Override
    public JsonObject statusNow() {
        return snapshots.status();
    }

    @Override
    public JsonArray playersNow() {
        return snapshots.players();
    }

    @Override
    public double[] tpsNow() {
        return Platform.tps();
    }

    @Override
    public double msptNow() {
        return Platform.mspt();
    }

    @Override
    public int onlineNow() {
        return Bukkit.getOnlinePlayers().size();
    }

    // ── Console ─────────────────────────────────────────────────────

    @Override
    public boolean runCommand(String command, List<String> output) throws Exception {
        return Tasks.await(scheduler.global(() -> {
            CommandSender sender = Platform.commandSender(output);
            return Bukkit.dispatchCommand(sender, command);
        }));
    }

    @Override
    public void runConsoleCommand(String command) {
        scheduler.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    @Override
    public JsonObject commands(List<String> blocked) throws Exception {
        return Tasks.await(scheduler.global(() -> Commands.list(blocked)));
    }

    @Override
    public CompletableFuture<JsonObject> complete(String buffer, List<String> blocked) {
        return scheduler.global(() -> Commands.complete(buffer, blocked));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void broadcast(String message) throws Exception {
        Tasks.await(scheduler.global(() -> {
            Bukkit.broadcastMessage(message);
            return null;
        }));
    }

    @Override
    public void notifyAdmins(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(RemoteMC.PERMISSION)) {
                p.sendMessage(message);
            }
        }
    }

    // ── Players ─────────────────────────────────────────────────────

    @Override
    public JsonObject whitelist() throws Exception {
        return Tasks.await(scheduler.global(Moderation::whitelist));
    }

    @Override
    public void setWhitelist(boolean on) throws Exception {
        Tasks.await(scheduler.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:whitelist " + (on ? "on" : "off"))));
    }

    @Override
    public boolean changeWhitelist(String name, boolean add) throws Exception {
        // The vanilla command also looks up players who never joined.
        return Tasks.await(scheduler.global(() -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:whitelist " + (add ? "add " : "remove ") + name);
            return Moderation.isWhitelisted(name);
        }));
    }

    @Override
    public JsonObject bans() throws Exception {
        return Tasks.await(scheduler.global(Moderation::bans));
    }

    @Override
    public void ban(String name, String reason) throws Exception {
        boolean ok = Tasks.await(scheduler.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "minecraft:ban " + name + (reason.isEmpty() ? "" : " " + reason))));
        if (!ok) {
            throw new ApiException(500, "action_failed", "Ban failed");
        }
    }

    @Override
    public boolean pardon(String name) throws Exception {
        return Tasks.await(scheduler.global(() -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:pardon " + name);
            return Moderation.isBanned(name);
        }));
    }

    @Override
    public JsonObject inventory(String name) throws Exception {
        Player p = online(name);
        // Read on the player's own thread (Folia).
        return Tasks.await(scheduler.entity(p, () -> Inventories.of(p)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void playerAction(String name, String action, JsonObject body, String reason) throws Exception {
        Player p = online(name);
        switch (action) {
            case "teleport" -> {
                String to = body.get("to").getAsString();
                Location target;
                if (to.equals("spawn")) {
                    target = Tasks.await(scheduler.global(() -> Bukkit.getWorlds().get(0).getSpawnLocation()));
                } else {
                    Player other = online(to);
                    // Read the other player's position on their own thread (Folia).
                    target = Tasks.await(scheduler.entity(other, other::getLocation));
                }
                onPlayer(p, () -> Platform.teleport(p, target));
            }
            case "kick" -> onPlayer(p, () -> p.kickPlayer(reason.isEmpty() ? "Kicked from the server" : reason));
            case "op" -> Tasks.await(scheduler.global(() -> { p.setOp(true); return null; }));
            case "deop" -> Tasks.await(scheduler.global(() -> { p.setOp(false); return null; }));
            case "heal" -> onPlayer(p, () -> {
                p.setHealth(Platform.maxHealth(p));
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.setFireTicks(0);
            });
            case "gamemode" -> {
                GameMode mode;
                try {
                    mode = GameMode.valueOf(body.get("mode").getAsString().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw ApiException.badRequest("bad_request", "Unknown game mode");
                }
                onPlayer(p, () -> p.setGameMode(mode));
            }
            case "message" -> {
                String message = body.get("message").getAsString();
                onPlayer(p, () -> p.sendMessage("§8[§aServer§8]§r " + message));
            }
            default -> throw ApiException.notFound("not_found", "Unknown action: " + action);
        }
    }

    private static Player online(String name) throws ApiException {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            throw ApiException.notFound("player_offline", "Player " + name + " is not online");
        }
        return p;
    }

    private void onPlayer(Player p, Runnable task) throws Exception {
        Tasks.await(scheduler.entity(p, () -> {
            task.run();
            return null;
        }));
    }

    // ── Worlds and plugins ──────────────────────────────────────────

    @Override
    public JsonObject world(String name) throws Exception {
        return Tasks.await(scheduler.global(Tasks.unchecked(() -> WorldControl.describe(WorldControl.world(name)))));
    }

    @Override
    public JsonObject worldAction(String name, String action, JsonObject body) throws Exception {
        return Tasks.await(scheduler.global(Tasks.unchecked(() -> {
            World w = WorldControl.world(name);
            switch (action) {
                case "time" -> WorldControl.setTime(w, body.get("time").getAsString());
                case "weather" -> WorldControl.setWeather(w, body.get("weather").getAsString());
                case "difficulty" -> WorldControl.setDifficulty(w, body.get("difficulty").getAsString());
                default -> WorldControl.setGameRule(w, body.get("rule").getAsString(), body.get("value"));
            }
            return WorldControl.describe(w);
        })));
    }

    @Override
    public JsonObject plugins(FileManager files) throws Exception {
        return Tasks.await(scheduler.global(() -> pluginList(files)));
    }

    @SuppressWarnings("deprecation") // getDescription works on Paper and Spigot alike
    private static JsonObject pluginList(FileManager files) {
        JsonArray arr = new JsonArray();
        Plugin[] all = Bukkit.getPluginManager().getPlugins();
        Arrays.sort(all, Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        for (Plugin p : all) {
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
            Path jar = jarOf(p);
            String file = jar == null ? null : files.relativeOrNull(jar);
            if (file != null) {
                o.addProperty("file", file); // so admins can remove it in the app
            }
            arr.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("plugins", arr);
        return o;
    }

    /** A plugin's JAR file, if it can be found. */
    private static Path jarOf(Plugin p) {
        try {
            java.security.CodeSource source = p.getClass().getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            Path jar = Path.of(source.getLocation().toURI());
            // Paper loads a remapped copy from plugins/.paper-remapped/; the file to manage is the original.
            for (Path parent = jar.getParent(); parent != null; parent = parent.getParent()) {
                if (parent.getFileName() != null && parent.getFileName().toString().equals(".paper-remapped")) {
                    jar = parent.getParent().resolve(jar.getFileName());
                    break;
                }
            }
            return Files.isRegularFile(jar) ? jar : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void pairingUsed(String code) {
        onPairingUsed.accept(code);
    }
}
