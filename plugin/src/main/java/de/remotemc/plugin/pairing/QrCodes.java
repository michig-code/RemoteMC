package de.remotemc.plugin.pairing;

import com.google.zxing.common.BitMatrix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.awt.Color;

/** Pairing QR codes as an in-game map item (Bukkit); encoding and text are in {@link QrText}. */
public final class QrCodes {

    private QrCodes() {}

    /** Map item showing the QR code. Must be called on the player's thread. */
    @SuppressWarnings("deprecation")
    /** Marks pairing maps with their code (removed when dropped or after pairing). */
    public static NamespacedKey codeKey(Plugin plugin) {
        return new NamespacedKey(plugin, "pair_code");
    }

    /** The pairing code of a QR map, or null for any other item. */
    public static String codeOf(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static ItemStack mapItem(Player player, BitMatrix m, String name, NamespacedKey key, String code) {
        MapView view = Bukkit.createMap(player.getWorld());
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new QrRenderer(m));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("§a" + name);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, code);
        item.setItemMeta(meta);
        return item;
    }

    private static final class QrRenderer extends MapRenderer {
        private final BitMatrix matrix;
        private boolean drawn;

        QrRenderer(BitMatrix matrix) {
            super(false);
            this.matrix = matrix;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) {
                return;
            }
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    canvas.setPixelColor(x, y, QrText.darkPixel(matrix, x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            drawn = true;
        }
    }
}
