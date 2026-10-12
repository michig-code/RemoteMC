package de.remotemc.plugin;

import de.remotemc.plugin.tls.TlsManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLServerSocket;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TlsTest {

    @Test
    void certificateIsCreatedOnceAndKeepsItsFingerprint(@TempDir Path dir) throws Exception {
        Logger logger = Logger.getLogger("test");
        TlsManager first = new TlsManager(dir.resolve("tls.p12").toFile(), logger);
        first.load();
        TlsManager second = new TlsManager(dir.resolve("tls.p12").toFile(), logger);
        second.load();

        assertEquals(first.fingerprintToken(), second.fingerprintToken());
        assertEquals(43, first.fingerprintToken().length());
        assertTrue(first.shortFingerprint().matches("[0-9A-F]{4}-[0-9A-F]{4}"));
        assertTrue(first.fingerprintHex().startsWith(first.shortFingerprint().replace("-", "").substring(0, 2)));

        // The context can actually serve TLS with the generated EC key.
        try (SSLServerSocket socket = (SSLServerSocket) first.context().getServerSocketFactory().createServerSocket(0)) {
            assertTrue(socket.getSupportedCipherSuites().length > 0);
        }
    }
}
