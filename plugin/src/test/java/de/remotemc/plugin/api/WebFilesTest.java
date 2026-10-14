package de.remotemc.plugin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebFilesTest {
    @Test
    void servesThePageAndItsFiles() {
        WebFiles.File page = WebFiles.find("/");
        assertNotNull(page);
        assertTrue(page.type().startsWith("text/html"));
        assertTrue(new String(page.bytes(), StandardCharsets.UTF_8).contains("app.js"));
        assertEquals("image/png", WebFiles.find("/icon.png").type());
        assertNotNull(WebFiles.find("/app.js"));
        assertNotNull(WebFiles.find("/style.css"));
        assertNotNull(WebFiles.find("/manifest.webmanifest"));
    }

    @Test
    void nothingElseFromTheJar() {
        assertNull(WebFiles.find("/config.yml"));
        assertNull(WebFiles.find("/plugin.yml"));
        assertNull(WebFiles.find("/../config.yml"));
        assertNull(WebFiles.find("/web/app.js"));
        assertNull(WebFiles.find("/lang/en.yml"));
    }
}
