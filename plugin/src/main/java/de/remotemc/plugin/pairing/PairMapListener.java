package de.remotemc.plugin.pairing;

import de.remotemc.plugin.auth.PairingManager;
import de.remotemc.plugin.platform.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Keeps pairing QR maps from piling up:
 * - a map the player throws away himself (Q key, dragged out of the inventory) is deleted;
 *   a map that fell down because the inventory was full stays, so it can be picked up;
 * - once a device paired with its code, the map is removed from every online player and
 *   disappears wherever it turns up later (chest, ground, a player who was offline).
 */
public final class PairMapListener implements Listener {

    private final NamespacedKey key;
    private final PairingManager pairing;
    private final Scheduler scheduler;

    public PairMapListener(NamespacedKey key, PairingManager pairing, Scheduler scheduler) {
        this.key = key;
        this.pairing = pairing;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (QrCodes.codeOf(event.getItemDrop().getItemStack(), key) != null) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (isUsed(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isUsed(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeUsed(event.getPlayer());
    }

    /** After pairing: takes the maps with that code from every online player. */
    public void paired(String code) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.entity(player, () -> {
                removeUsed(player);
                return null;
            });
        }
    }

    private boolean isUsed(ItemStack item) {
        return pairing.isUsed(QrCodes.codeOf(item, key));
    }

    private void removeUsed(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents(); // includes armour and off hand
        for (int i = 0; i < contents.length; i++) {
            if (isUsed(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        if (isUsed(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }
}
