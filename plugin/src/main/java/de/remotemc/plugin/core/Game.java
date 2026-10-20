package de.remotemc.plugin.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.remotemc.plugin.api.FileManager;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * What RemoteMC needs from the server software: Paper/Spigot/Folia ({@code BukkitGame}) or Fabric.
 * Everything else – API, pairing, tokens, console, chat, history, tasks – is shared.
 *
 * <p>Methods ending in "Now" run on the server thread; all others may be called from any thread,
 * wait for the server thread themselves and report problems as {@link de.remotemc.plugin.api.ApiException}.
 * The JSON is the same on every platform, so the apps can't tell the difference.
 */
public interface Game {

    /** "Paper", "Folia", "Spigot" or "Fabric". */
    String platformName();

    /** Whether {@link #runCommand} returns what the command printed. */
    boolean capturesCommandOutput();

    /** The server folder (the app's file manager starts there). */
    Path serverFolder();

    /** RemoteMC's own JAR, so the app cannot delete it; null if unknown. */
    Path ownJar();

    /** server-ip of server.properties, or blank. */
    String serverIp();

    /** Runs a task on the server thread and returns its result. */
    <T> CompletableFuture<T> global(Supplier<T> task);

    /** Runs {@code task} on the server thread every {@code ticks}; the result cancels it. */
    Runnable repeat(Runnable task, long ticks);

    // ── Status ──────────────────────────────────────────────────────

    JsonObject statusNow();

    JsonArray playersNow();

    /** TPS (1, 5, 15 min; empty if unknown), tick time in ms (-1 if unknown) and players online. */
    double[] tpsNow();

    double msptNow();

    int onlineNow();

    // ── Console ─────────────────────────────────────────────────────

    /** Runs a command as the console, capturing its output where the server allows; true if it ran. */
    boolean runCommand(String command, List<String> output) throws Exception;

    /** Runs a command as the console without waiting (scheduled tasks). */
    void runConsoleCommand(String command);

    JsonObject commands(List<String> blocked) throws Exception;

    CompletableFuture<JsonObject> complete(String buffer, List<String> blocked);

    /** Sends a chat message (§ colour codes) to everybody. */
    void broadcast(String message) throws Exception;

    /** Shows a message to the console and to players allowed to use /remotemc. */
    void notifyAdmins(String message);

    // ── Players ─────────────────────────────────────────────────────

    JsonObject whitelist() throws Exception;

    void setWhitelist(boolean on) throws Exception;

    /** Adds or removes a player (also one who never joined); returns whether they are listed afterwards. */
    boolean changeWhitelist(String name, boolean add) throws Exception;

    JsonObject bans() throws Exception;

    void ban(String name, String reason) throws Exception;

    /** Returns whether the player is still banned afterwards. */
    boolean pardon(String name) throws Exception;

    JsonObject inventory(String name) throws Exception;

    /** kick, op, deop, heal, gamemode (body "mode"), message (body "message") or teleport (body "to"). */
    void playerAction(String name, String action, JsonObject body, String reason) throws Exception;

    // ── Worlds and plugins ──────────────────────────────────────────

    JsonObject world(String name) throws Exception;

    /** time, weather, difficulty or gamerule; returns the world afterwards. */
    JsonObject worldAction(String name, String action, JsonObject body) throws Exception;

    /** Plugins (Bukkit) or mods (Fabric), each with the file the app may manage. */
    JsonObject plugins(FileManager files) throws Exception;

    /** A pairing code was used: remove its QR maps. */
    void pairingUsed(String code);
}
