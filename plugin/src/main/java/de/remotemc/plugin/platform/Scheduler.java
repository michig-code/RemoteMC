package de.remotemc.plugin.platform;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Runs work on the right server thread for Bukkit/Paper (main thread) or Folia (region threads). */
public interface Scheduler {

    /** Server-wide work: commands, broadcasts, world/server queries. */
    <T> CompletableFuture<T> global(Supplier<T> task);

    /** Work that touches one entity (kick, game mode, health). */
    <T> CompletableFuture<T> entity(Entity entity, Supplier<T> task);

    /** Repeating server-wide task; returns a handle to cancel it. */
    Runnable repeat(Runnable task, long periodTicks);

    static Scheduler create(Plugin plugin) {
        return Platform.FOLIA ? new FoliaScheduler(plugin) : new BukkitScheduler(plugin);
    }
}
