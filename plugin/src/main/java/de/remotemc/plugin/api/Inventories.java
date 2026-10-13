package de.remotemc.plugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** A read-only look into a player's inventory and ender chest. Call on the player's thread. */
final class Inventories {

    private Inventories() {}

    static JsonObject of(Player p) {
        PlayerInventory inv = p.getInventory();
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getName());
        // 0–8 hotbar, 9–35 main inventory.
        o.add("items", items(inv.getStorageContents()));
        // Boots, leggings, chestplate, helmet (Bukkit's order).
        o.add("armor", items(inv.getArmorContents()));
        o.add("offhand", items(new ItemStack[] {inv.getItemInOffHand()}));
        o.add("enderChest", items(p.getEnderChest().getContents()));
        o.addProperty("selectedSlot", inv.getHeldItemSlot());
        return o;
    }

    /** Non-empty slots only, each with its index. */
    static JsonArray items(ItemStack[] stacks) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < stacks.length; i++) {
            JsonObject item = item(stacks[i]);
            if (item != null) {
                item.addProperty("slot", i);
                arr.add(item);
            }
        }
        return arr;
    }

    static JsonObject item(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("id", stack.getType().getKey().toString());
        o.addProperty("amount", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                @SuppressWarnings("deprecation") // Adventure's displayName() is Paper-only
                String name = ChatColor.stripColor(meta.getDisplayName());
                o.addProperty("name", name);
            }
            if (!meta.getEnchants().isEmpty()) {
                JsonArray enchants = new JsonArray();
                meta.getEnchants().forEach((e, level) -> enchants.add(e.getKey().getKey() + " " + level));
                o.add("enchants", enchants);
            }
            int max = stack.getType().getMaxDurability();
            if (max > 0 && meta instanceof Damageable d) {
                o.addProperty("durability", Math.max(0, max - d.getDamage()));
                o.addProperty("maxDurability", max);
            }
        }
        return o;
    }
}
