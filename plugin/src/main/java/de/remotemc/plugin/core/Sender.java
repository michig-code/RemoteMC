package de.remotemc.plugin.core;

import com.google.zxing.common.BitMatrix;

/** Who ran /remotemc: the console or a player, on any server software. Texts use § colour codes. */
public interface Sender {
    String name();

    boolean isPlayer();

    boolean hasPermission();

    void send(String text);

    /** {@code prefix} followed by {@code text} in aqua; clicking copies {@code value} or opens it as a link. */
    void sendLink(String prefix, String text, String value, boolean open, String hover);

    /** Gives the player a map showing the QR code (marked with the pairing code); false for the console. */
    boolean giveQrMap(BitMatrix qr, String name, String code);
}
