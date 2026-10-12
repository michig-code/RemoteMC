package de.remotemc.plugin.auth;

import java.util.Locale;

/** What a paired device may do. Each role includes the rights of the roles before it. */
public enum Role {
    /** Status, players and chat history. */
    VIEWER,
    /** + console output, chat, messages, kick, ban, heal, game mode. */
    MODERATOR,
    /** + console commands, OP/DeOP. */
    ADMIN;

    public boolean atLeast(Role other) {
        return ordinal() >= other.ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
