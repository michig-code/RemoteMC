package de.remotemc.plugin.pairing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Renders pairing QR codes as an in-game map item or as text for the console. */
public final class QrCodes {

    private QrCodes() {}

    public static BitMatrix encode(String content) {
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
        } catch (WriterException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Whether the server console can print the block characters used by {@link #toText}. */
    public static boolean consoleSupportsBlocks() {
        try {
            Charset charset = Charset.forName(System.getProperty("stdout.encoding", Charset.defaultCharset().name()));
            return charset.newEncoder().canEncode("█▀▄");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Text QR using half blocks (two modules per character row). Light modules are drawn as
     * blocks, which gives the right contrast on the usual dark console background.
     */
    public static List<String> toText(BitMatrix m) {
        int quiet = 2;
        int size = m.getWidth();
        List<String> lines = new ArrayList<>();
        for (int y = -quiet; y < size + quiet; y += 2) {
            StringBuilder sb = new StringBuilder();
            for (int x = -quiet; x < size + quiet; x++) {
                boolean top = !dark(m, x, y);
                boolean bottom = !dark(m, x, y + 1);
                sb.append(top && bottom ? '█' : top ? '▀' : bottom ? '▄' : ' ');
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    private static boolean dark(BitMatrix m, int x, int y) {
        return x >= 0 && y >= 0 && x < m.getWidth() && y < m.getHeight() && m.get(x, y);
    }

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
            int size = matrix.getWidth();
            int scale = Math.max(1, 120 / size);
            int offset = (128 - size * scale) / 2;
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    int mx = (x - offset) / scale;
                    int my = (y - offset) / scale;
                    boolean dark = x >= offset && y >= offset && mx < size && my < size && matrix.get(mx, my);
                    canvas.setPixelColor(x, y, dark ? Color.BLACK : Color.WHITE);
                }
            }
            drawn = true;
        }
    }
}
