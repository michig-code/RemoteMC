package de.remotemc.plugin.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Only loaded on Folia (the scheduler APIs exist in Paper too, but Folia requires them). */
final class FoliaScheduler implements Scheduler {

    private final Plugin plugin;

    FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public <T> CompletableFuture<T> global(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> BukkitScheduler.complete(future, task));
        return future;
    }

    @Override
    public <T> CompletableFuture<T> entity(Entity entity, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        boolean scheduled = entity.getScheduler().execute(plugin, () -> BukkitScheduler.complete(future, task),
                () -> future.completeExceptionally(new IllegalStateException("Entity no longer exists")), 1L);
        if (!scheduled) {
            future.completeExceptionally(new IllegalStateException("Entity no longer exists"));
        }
        return future;
    }

    @Override
    public Runnable repeat(Runnable task, long periodTicks) {
        ScheduledTask handle = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), periodTicks, periodTicks);
        return handle::cancel;
    }
}
