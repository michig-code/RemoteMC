package de.remotemc.plugin.live;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Joins and leaves for the chat history; status updates follow via the periodic snapshot. */
public final class GameListener implements Listener {

    private final ChatBuffer chat;

    public GameListener(ChatBuffer chat) {
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        chat.add(ChatBuffer.Type.JOIN, event.getPlayer().getName(), "");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        chat.add(ChatBuffer.Type.QUIT, event.getPlayer().getName(), "");
    }
}
