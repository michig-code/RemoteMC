package de.remotemc.plugin;

import com.google.zxing.common.BitMatrix;
import de.remotemc.plugin.api.BukkitGame;
import de.remotemc.plugin.core.RemoteMC;
import de.remotemc.plugin.core.Sender;
import de.remotemc.plugin.core.Settings;
import de.remotemc.plugin.live.GameListener;
import de.remotemc.plugin.live.PaperChatListener;
import de.remotemc.plugin.live.SpigotChatListener;
import de.remotemc.plugin.pairing.PairMapListener;
import de.remotemc.plugin.pairing.QrCodes;
import de.remotemc.plugin.platform.Platform;
import de.remotemc.plugin.platform.Scheduler;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** RemoteMC as a Paper/Spigot/Folia plugin: Bukkit glue around the shared {@link RemoteMC} core. */
public final class RemoteMCPlugin extends JavaPlugin implements Listener {

    public static final String PERMISSION = RemoteMC.PERMISSION;

    private RemoteMC core;
    private Scheduler scheduler;
    private NamespacedKey mapKey;
    private PairMapListener pairMaps;

    @Override
    public void onEnable() {
        importFromMcRemote();
        saveDefaultConfig();
        // Adds settings introduced in newer versions while keeping the user's values.
        getConfig().options().copyDefaults(true);
        String legacy = getConfig().getString("api.token", "");
        getConfig().set("api.token", null);
        saveConfig();

        scheduler = Scheduler.create(this);
        mapKey = QrCodes.codeKey(this);
        BukkitGame game = new BukkitGame(this, scheduler, code -> {
            if (pairMaps != null) {
                pairMaps.paired(code);
            }
        });
        core = new RemoteMC(getDataFolder(), getLogger(), getDescription().getVersion(), new BukkitSettings(), game);
        game.bind(core);
        core.start();
        if (legacy != null && !legacy.isBlank()) {
            core.tokens().importLegacy(legacy);
            getLogger().info("Moved the old access token to tokens.yml (name: Legacy token).");
        }

        pairMaps = new PairMapListener(mapKey, core.pairing(), scheduler);
        getServer().getPluginManager().registerEvents(new GameListener(core.chat()), this);
        getServer().getPluginManager().registerEvents(
                Platform.PAPER ? new PaperChatListener(core.chat()) : new SpigotChatListener(core.chat()), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(pairMaps, this);
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.stop();
        }
    }

    /** The prototype was called McRemote; take over its settings and token on first start. */
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        core.onJoin(new BukkitSender(event.getPlayer()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        core.command(new BukkitSender(sender), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.hasPermission(PERMISSION) ? core.tabComplete(args) : Collections.emptyList();
    }

    /** config.yml through Bukkit (keeps the comments and adds new settings). */
    private final class BukkitSettings implements Settings {
        @Override
        public String getString(String path, String def) {
            return getConfig().getString(path, def);
        }

        @Override
        public int getInt(String path, int def) {
            return getConfig().getInt(path, def);
        }

        @Override
        public long getLong(String path, long def) {
            return getConfig().getLong(path, def);
        }

        @Override
        public boolean getBoolean(String path, boolean def) {
            return getConfig().getBoolean(path, def);
        }

        @Override
        public List<String> getStringList(String path) {
            return getConfig().getStringList(path);
        }

        @Override
        public void reload() {
            reloadConfig();
        }
    }

    /** A Bukkit command sender (console or player). */
    private final class BukkitSender implements Sender {
        private final CommandSender sender;

        BukkitSender(CommandSender sender) {
            this.sender = sender;
        }

        @Override
        public String name() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public boolean hasPermission() {
            return sender.hasPermission(PERMISSION);
        }

        @Override
        public void send(String text) {
            sender.sendMessage(text);
        }

        @Override
        public void sendLink(String prefix, String text, String value, boolean open, String hover) {
            TextComponent line = new TextComponent(prefix);
            TextComponent link = new TextComponent(text);
            link.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            link.setClickEvent(new ClickEvent(open ? ClickEvent.Action.OPEN_URL : ClickEvent.Action.COPY_TO_CLIPBOARD, value));
            if (hover != null) {
                link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
            }
            line.addExtra(link);
            sender.spigot().sendMessage(line);
        }

        @Override
        public boolean giveQrMap(BitMatrix qr, String name, String code) {
            if (!(sender instanceof Player player)) {
                return false;
            }
            scheduler.entity(player, () -> {
                ItemStack map = QrCodes.mapItem(player, qr, name, mapKey, code);
                player.getInventory().addItem(map).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
                return null;
            });
            return true;
        }
    }
}
