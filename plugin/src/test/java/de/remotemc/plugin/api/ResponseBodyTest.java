package de.remotemc.plugin.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseBodyTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void etagDependsOnlyOnTheContent() {
        String a = ResponseBody.etag(bytes("{\"players\":[]}"));
        assertEquals(a, ResponseBody.etag(bytes("{\"players\":[]}")));
        assertNotEquals(a, ResponseBody.etag(bytes("{\"players\":[\"Steve\"]}")));
        assertTrue(a.startsWith("W/\"") && a.endsWith("\""));
    }

    @Test
    void notModifiedMatchesWeakStrongListsAndStar() {
        String etag = ResponseBody.etag(bytes("x"));
        String bare = etag.substring(2);
        assertTrue(ResponseBody.notModified(etag, etag));
        assertTrue(ResponseBody.notModified(bare, etag));
        assertTrue(ResponseBody.notModified("W/\"other\", " + etag, etag));
        assertTrue(ResponseBody.notModified("*", etag));
        assertFalse(ResponseBody.notModified(null, etag));
        assertFalse(ResponseBody.notModified("", etag));
        assertFalse(ResponseBody.notModified("W/\"other\"", etag));
    }

    @Test
    void gzipOnlyWhenAcceptedAndWorthIt() {
        int big = ResponseBody.MIN_GZIP;
        assertTrue(ResponseBody.shouldGzip("gzip", big));
        assertTrue(ResponseBody.shouldGzip("deflate, GZIP;q=0.5", big));
        assertFalse(ResponseBody.shouldGzip("gzip", big - 1));
        assertFalse(ResponseBody.shouldGzip(null, big));
        assertFalse(ResponseBody.shouldGzip("br, deflate", big));
        assertFalse(ResponseBody.shouldGzip("gzip;q=0", big));
        assertFalse(ResponseBody.shouldGzip("x-gzip", big));
    }

    @Test
    void gzipRoundTripIsSmallerForRepetitiveJson() throws IOException {
        byte[] body = bytes("{\"line\":\"[12:00:00 INFO]: Steve joined the game\"},".repeat(200));
        byte[] packed = ResponseBody.gzip(body);
        assertTrue(packed.length < body.length / 5, "gzip should shrink console output a lot");
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            assertArrayEquals(body, in.readAllBytes());
        }
    }
}
