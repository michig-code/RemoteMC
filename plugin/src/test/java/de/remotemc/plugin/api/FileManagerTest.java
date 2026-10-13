package de.remotemc.plugin.api;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagerTest {

    @TempDir
    Path base;
    Path server;
    FileManager files;

    @BeforeEach
    void setUp() throws Exception {
        server = Files.createDirectories(base.resolve("server"));
        Files.createDirectories(server.resolve("plugins/RemoteMC"));
        Files.writeString(server.resolve("plugins/RemoteMC/tokens.yml"), "secret");
        Files.createDirectories(server.resolve("plugins/Essentials"));
        Files.writeString(server.resolve("plugins/Essentials/config.yml"), "motd: hi\n");
        Files.writeString(server.resolve("server.properties"), "motd=Test\n");
        Files.writeString(base.resolve("outside.txt"), "nope");
        files = new FileManager(server, server.resolve("plugins/RemoteMC"));
    }

    private static int status(ThrowingRunnable r) {
        return assertThrows(ApiException.class, r::run).status;
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void listsFoldersFirstAndHidesRemoteMC() throws Exception {
        JsonObject root = files.list("");
        assertEquals("plugins", root.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        String plugins = files.list("plugins").toString();
        assertTrue(plugins.contains("Essentials"));
        assertFalse(plugins.contains("RemoteMC"));
    }

    @Test
    void staysInsideTheServerFolder() throws Exception {
        assertEquals(403, status(() -> files.read("../outside.txt")));
        assertEquals(403, status(() -> files.read("plugins/../../outside.txt")));
        assertEquals(403, status(() -> files.list("..")));
        assertEquals(400, status(() -> files.read("C:/Windows/win.ini")));
        // a leading slash means the server folder, not the file system root
        assertEquals("motd=Test\n", files.read("/server.properties").get("content").getAsString());
    }

    @Test
    void remoteMCFilesAreUnreachable() {
        assertEquals(403, status(() -> files.read("plugins/RemoteMC/tokens.yml")));
        assertEquals(403, status(() -> files.write("plugins/RemoteMC/tokens.yml", "x")));
        assertEquals(403, status(() -> files.delete("plugins/RemoteMC")));
        assertEquals(403, status(() -> files.delete("plugins"))); // contains RemoteMC
        assertEquals(403, status(() -> files.delete("")));
    }

    @Test
    void symlinksOutsideAreRefused() throws Exception {
        try {
            Files.createSymbolicLink(server.resolve("escape"), base);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // no symlinks on this file system
        }
        assertEquals(403, status(() -> files.read("escape/outside.txt")));
        assertEquals(403, status(() -> files.write("escape/new.txt", "x")));
    }

    @Test
    void editsTextFiles() throws Exception {
        files.write("plugins/Essentials/config.yml", "motd: hello äöü\n");
        assertEquals("motd: hello äöü\n", files.read("plugins/Essentials/config.yml").get("content").getAsString());
        files.write("notes.txt", "new file");
        assertEquals("new file", Files.readString(server.resolve("notes.txt")));
        assertEquals(404, status(() -> files.write("missing/dir/a.txt", "x")));
    }

    @Test
    void refusesBinaryAndHugeFiles() throws Exception {
        Files.write(server.resolve("world.dat"), new byte[] {1, 0, 2, 3});
        assertEquals(415, status(() -> files.read("world.dat")));
        Files.write(server.resolve("big.log"), new byte[FileManager.MAX_TEXT + 1]);
        assertEquals(413, status(() -> files.read("big.log")));
    }

    @Test
    void uploadsAndDeletes() throws Exception {
        byte[] jar = "PK\u0003\u0004fake".getBytes(StandardCharsets.ISO_8859_1);
        JsonObject info = files.upload("plugins/Fake.jar", new ByteArrayInputStream(jar), jar.length);
        assertEquals("plugins/Fake.jar", info.get("path").getAsString());
        assertEquals(jar.length, Files.size(server.resolve("plugins/Fake.jar")));
        assertEquals("plugins/Fake.jar", files.relativeOrNull(server.resolve("plugins/Fake.jar")));

        files.delete("plugins/Fake.jar");
        assertFalse(Files.exists(server.resolve("plugins/Fake.jar")));
        files.delete("plugins/Essentials");
        assertFalse(Files.exists(server.resolve("plugins/Essentials")));
        assertEquals(404, status(() -> files.delete("plugins/Essentials")));

        files.mkdir("backups/today");
        assertTrue(Files.isDirectory(server.resolve("backups/today")));
        assertEquals(409, status(() -> files.mkdir("backups")));
        // no temp files left behind
        try (var s = Files.list(server)) {
            assertTrue(s.noneMatch(p -> p.getFileName().toString().startsWith(".remotemc-")));
        }
    }

    @Test
    void deletedFilesCanBeRestoredForADay() throws Exception {
        files.useTrash(server.resolve("plugins/RemoteMC/trash"));
        String id = files.delete("plugins/Essentials");
        assertTrue(id != null && id.matches("[0-9]+-[0-9a-f]+"), id);
        assertFalse(Files.exists(server.resolve("plugins/Essentials")));
        // The trash is inside RemoteMC's hidden folder: not listed, not reachable.
        assertFalse(files.list("plugins").toString().contains("RemoteMC"));
        assertEquals(403, status(() -> files.list("plugins/RemoteMC/trash")));

        files.restore(id);
        assertEquals("motd: hi\n", Files.readString(server.resolve("plugins/Essentials/config.yml")));
        assertEquals(404, status(() -> files.restore(id))); // only once

        String again = files.delete("server.properties");
        Files.writeString(server.resolve("server.properties"), "new");
        assertEquals(409, status(() -> files.restore(again))); // something new is there now
        assertEquals(404, status(() -> files.restore("../../etc")));
    }

    @Test
    void trashIsEmptiedAfterADay() throws Exception {
        files.useTrash(server.resolve("plugins/RemoteMC/trash"));
        String id = files.delete("server.properties");
        files.purgeTrash(System.currentTimeMillis() + FileManager.TRASH_KEEP_MILLIS + 1000);
        assertEquals(404, status(() -> files.restore(id)));
        assertFalse(Files.exists(server.resolve("plugins/RemoteMC/trash/" + id)));
    }
}
