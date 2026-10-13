package de.remotemc.plugin.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Makes JSON answers smaller on the wire: gzip when the client accepts it, and an ETag so a
 * client that already has the same answer gets an empty "304 Not Modified" instead.
 */
final class ResponseBody {
    /** Smaller answers are not worth compressing (gzip adds about 20 bytes). */
    static final int MIN_GZIP = 1024;

    private ResponseBody() {
    }

    /** Weak ETag of the uncompressed body, so it is the same with and without gzip. */
    static String etag(byte[] body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
            return "W/\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** True when the client's If-None-Match header already names [etag]. */
    static boolean notModified(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String bare = stripWeak(etag);
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.trim();
            if (c.equals("*") || stripWeak(c).equals(bare)) {
                return true;
            }
        }
        return false;
    }

    private static String stripWeak(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }

    /** True when the Accept-Encoding header allows gzip and the body is big enough. */
    static boolean shouldGzip(String acceptEncoding, int length) {
        if (acceptEncoding == null || length < MIN_GZIP) {
            return false;
        }
        for (String part : acceptEncoding.toLowerCase(Locale.ROOT).split(",")) {
            String[] coding = part.trim().split(";");
            if (!coding[0].trim().equals("gzip")) {
                continue;
            }
            // "gzip;q=0" means "not gzip".
            for (int i = 1; i < coding.length; i++) {
                String param = coding[i].trim();
                if (param.startsWith("q=")) {
                    try {
                        return Double.parseDouble(param.substring(2)) > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    static byte[] gzip(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, body.length / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
