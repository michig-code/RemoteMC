package de.remotemc.plugin.live;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Chat on Paper/Folia (registered only there). */
public final class PaperChatListener implements Listener {

    private final ChatBuffer chat;

    public PaperChatListener(ChatBuffer chat) {
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        chat.add(ChatBuffer.Type.CHAT, event.getPlayer().getName(),
                PlainTextComponentSerializer.plainText().serialize(event.message()));
    }
}
