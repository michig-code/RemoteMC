package de.remotemc.plugin.live;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Chat on Spigot, which has no Adventure-based chat event. */
@SuppressWarnings("deprecation")
public final class SpigotChatListener implements Listener {

    private final ChatBuffer chat;

    public SpigotChatListener(ChatBuffer chat) {
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        chat.add(ChatBuffer.Type.CHAT, event.getPlayer().getName(), event.getMessage());
    }
}
