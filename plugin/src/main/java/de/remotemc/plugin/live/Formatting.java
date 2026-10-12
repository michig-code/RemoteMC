package de.remotemc.plugin.live;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Console colours for the app. Log lines may contain Minecraft section codes (§a) or ANSI escape
 * sequences (from the server's console renderer); both are normalised to section codes, which the
 * app renders. {@link #strip} removes all formatting.
 */
public final class Formatting {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[([0-9;]*)m");
    private static final Pattern ANY = Pattern.compile("\u001B\\[[0-9;]*m|§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);

    private Formatting() {}

    public static String strip(String text) {
        return ANY.matcher(text).replaceAll("");
    }

    /** Converts ANSI colour escapes to section codes; existing section codes are kept. */
    public static String toSectionCodes(String text) {
        if (text.indexOf('\u001B') < 0) {
            return text;
        }
        Matcher m = ANSI.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(convert(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String convert(String params) {
        if (params.isEmpty()) {
            return "§r";
        }
        String[] p = params.split(";");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length; i++) {
            int code;
            try {
                code = Integer.parseInt(p[i]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (code == 38 && i + 4 < p.length && p[i + 1].equals("2")) {
                sb.append(hex(p[i + 2], p[i + 3], p[i + 4]));
                i += 4;
                continue;
            }
            if (code == 38 && i + 2 < p.length && p[i + 1].equals("5")) {
                i += 2; // 256-colour palette: not mapped
                continue;
            }
            String mapped = switch (code) {
                case 0, 39 -> "§r";
                case 1 -> "§l";
                case 3 -> "§o";
                case 4 -> "§n";
                case 9 -> "§m";
                case 30 -> "§0";
                case 31 -> "§4";
                case 32 -> "§2";
                case 33 -> "§6";
                case 34 -> "§1";
                case 35 -> "§5";
                case 36 -> "§3";
                case 37 -> "§7";
                case 90 -> "§8";
                case 91 -> "§c";
                case 92 -> "§a";
                case 93 -> "§e";
                case 94 -> "§9";
                case 95 -> "§d";
                case 96 -> "§b";
                case 97 -> "§f";
                default -> "";
            };
            sb.append(mapped);
        }
        return sb.toString();
    }

    private static String hex(String r, String g, String b) {
        try {
            String h = String.format("%02x%02x%02x", Integer.parseInt(r), Integer.parseInt(g), Integer.parseInt(b));
            StringBuilder sb = new StringBuilder("§x");
            for (char c : h.toCharArray()) {
                sb.append('§').append(c);
            }
            return sb.toString();
        } catch (NumberFormatException | java.util.IllegalFormatException e) {
            return "";
        }
    }
}
