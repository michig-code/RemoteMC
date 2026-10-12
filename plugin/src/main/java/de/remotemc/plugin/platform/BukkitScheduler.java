package de.remotemc.plugin.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class BukkitScheduler implements Scheduler {

    private final Plugin plugin;

    BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public <T> CompletableFuture<T> global(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            complete(future, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> complete(future, task));
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> entity(Entity entity, Supplier<T> task) {
        return global(task);
    }

    @Override
    public Runnable repeat(Runnable task, long periodTicks) {
        BukkitTask handle = Bukkit.getScheduler().runTaskTimer(plugin, task, periodTicks, periodTicks);
        return handle::cancel;
    }

    static <T> void complete(CompletableFuture<T> future, Supplier<T> task) {
        try {
            future.complete(task.get());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }
}
