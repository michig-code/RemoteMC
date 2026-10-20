package de.remotemc.plugin.pairing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pairing QR codes: encoding, text for the console and the pixels of a 128×128 map. */
public final class QrText {

    private QrText() {}

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

    /** Whether pixel (x, y) of a 128×128 map showing the code is dark; the code is centred and scaled up. */
    public static boolean darkPixel(BitMatrix m, int x, int y) {
        int size = m.getWidth();
        int scale = Math.max(1, 120 / size);
        int offset = (128 - size * scale) / 2;
        int mx = (x - offset) / scale;
        int my = (y - offset) / scale;
        return x >= offset && y >= offset && mx < size && my < size && m.get(mx, my);
    }

    private static boolean dark(BitMatrix m, int x, int y) {
        return x >= 0 && y >= 0 && x < m.getWidth() && y < m.getHeight() && m.get(x, y);
    }
}
