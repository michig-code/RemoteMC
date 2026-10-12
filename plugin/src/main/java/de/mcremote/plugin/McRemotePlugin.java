package de.mcremote.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class McRemotePlugin extends JavaPlugin {

    private ConsoleBuffer consoleBuffer;
    private ApiServer apiServer;
    private final long startedAt = System.currentTimeMillis();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureToken();

        consoleBuffer = new ConsoleBuffer(getConfig().getInt("console.buffer-size", 500));
        consoleBuffer.attach();

        startApi();
    }

    @Override
    public void onDisable() {
        stopApi();
        if (consoleBuffer != null) {
            consoleBuffer.detach();
        }
    }

    private void startApi() {
        apiServer = new ApiServer(this, consoleBuffer);
        try {
            apiServer.start();
            getLogger().info("McRemote API listening on " + getConfig().getString("api.host") + ":" + getConfig().getInt("api.port"));
        } catch (Exception e) {
            getLogger().severe("Could not start McRemote API: " + e.getMessage());
            apiServer = null;
        }
    }

    private void stopApi() {
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
    }

    private void ensureToken() {
        String token = getConfig().getString("api.token", "");
        if (token == null || token.isBlank()) {
            regenerateToken();
            getLogger().info("Generated new McRemote access token. Use /mcremote info to view it.");
        }
    }

    private void regenerateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        getConfig().set("api.token", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        saveConfig();
    }

    public long startedAt() {
        return startedAt;
    }

    String connectLink() {
        String address = getConfig().getString("api.public-address", "");
        if (address == null || address.isBlank()) {
            address = Bukkit.getIp();
        }
        if (address == null || address.isBlank()) {
            address = "localhost";
        }
        return "mcremote://" + address + ":" + getConfig().getInt("api.port")
                + "?token=" + URLEncoder.encode(getConfig().getString("api.token", ""), StandardCharsets.UTF_8);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                sender.sendMessage(Component.text("McRemote", NamedTextColor.GOLD)
                        .append(Component.text(apiServer != null ? " – API läuft" : " – API gestoppt",
                                apiServer != null ? NamedTextColor.GREEN : NamedTextColor.RED)));
                sender.sendMessage(Component.text("Port: ", NamedTextColor.GRAY)
                        .append(Component.text(getConfig().getInt("api.port"), NamedTextColor.WHITE)));
                String token = getConfig().getString("api.token", "");
                sender.sendMessage(Component.text("Token: ", NamedTextColor.GRAY)
                        .append(Component.text(token, NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.copyToClipboard(token))
                                .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Kopieren")))));
                sender.sendMessage(Component.text("/mcremote link", NamedTextColor.YELLOW)
                        .append(Component.text(" zeigt den Verbindungslink für die App.", NamedTextColor.GRAY)));
            }
            case "link" -> {
                String link = connectLink();
                sender.sendMessage(Component.text("Verbindungslink (in der App einfügen):", NamedTextColor.GRAY));
                sender.sendMessage(Component.text(link, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.copyToClipboard(link))
                        .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Kopieren"))));
            }
            case "newtoken" -> {
                regenerateToken();
                sender.sendMessage(Component.text("Neuer Token erzeugt. Alte Verbindungen sind ungültig.", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Token: ", NamedTextColor.GRAY)
                        .append(Component.text(getConfig().getString("api.token", ""), NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.copyToClipboard(getConfig().getString("api.token", "")))));
            }
            case "reload" -> {
                stopApi();
                reloadConfig();
                ensureToken();
                consoleBuffer.resize(getConfig().getInt("console.buffer-size", 500));
                startApi();
                sender.sendMessage(Component.text("McRemote neu geladen.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Benutzung: /" + label + " <info|link|newtoken|reload>", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("info", "link", "newtoken", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
