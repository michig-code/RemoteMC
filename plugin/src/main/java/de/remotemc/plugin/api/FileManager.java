package de.remotemc.plugin.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Files of the server folder for admins: list, read and write text files, upload, delete and
 * create folders. Everything stays inside the server folder (no "..", no absolute paths, no
 * symbolic links pointing outside), and RemoteMC's own folder with the tokens and the TLS key is
 * neither shown nor reachable. Paths are relative to the server folder with "/" as separator.
 */
public final class FileManager {

    /** Text files up to this size can be opened in the editor. */
    public static final int MAX_TEXT = 1024 * 1024;
    /** Largest upload (plugins are usually a few MB). */
    public static final long MAX_UPLOAD = 200L * 1024 * 1024;

    private final Path root;
    private final List<Path> hidden = new ArrayList<>();
    /** Deleted files wait here for [TRASH_KEEP_MILLIS] (inside a hidden folder); null = delete at once. */
    private Path trash;
    public static final long TRASH_KEEP_MILLIS = 24L * 60 * 60 * 1000;
    private static final String ORIGIN = ".origin";

    /** [hidden]: folders or files that are never listed or touched (e.g. plugins/RemoteMC). */
    public FileManager(Path root, Path... hidden) throws IOException {
        this.root = root.toRealPath();
        for (Path h : hidden) {
            Path abs = h.isAbsolute() ? h : this.root.resolve(h);
            this.hidden.add(Files.exists(abs) ? abs.toRealPath() : abs.normalize());
        }
    }

    /** Deleted files are moved into [dir] (must be in a hidden folder) and can be restored for a day. */
    public void useTrash(Path dir) throws IOException {
        Files.createDirectories(dir);
        trash = dir.toRealPath();
        purgeTrash(System.currentTimeMillis());
    }

    /** Resolves a relative path safely; "" is the server folder itself. */
    Path resolve(String relative) throws ApiException {
        String rel = relative == null ? "" : relative.replace('\\', '/').strip();
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        if (rel.indexOf('\0') >= 0 || rel.length() > 1024 || rel.matches("^[A-Za-z]:.*")) {
            throw ApiException.badRequest("bad_path", "Invalid path");
        }
        for (String part : rel.split("/")) {
            if (part.equals("..")) {
                throw ApiException.forbidden("Paths outside the server folder are not allowed");
            }
        }
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.forbidden("Paths outside the server folder are not allowed");
        }
        // A symbolic link may point anywhere: check where the existing part really is.
        Path existing = p;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing != null) {
            try {
                if (!existing.toRealPath().startsWith(root)) {
                    throw ApiException.forbidden("Paths outside the server folder are not allowed");
                }
            } catch (IOException e) {
                throw ApiException.forbidden("Path cannot be checked");
            }
        }
        if (isHidden(p)) {
            throw ApiException.forbidden("RemoteMC's own files (tokens, certificate) cannot be accessed");
        }
        return p;
    }

    private boolean isHidden(Path p) {
        for (Path h : hidden) {
            if (p.startsWith(h)) {
                return true;
            }
        }
        return false;
    }

    private String relative(Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    public JsonObject list(String relative) throws ApiException, IOException {
        Path dir = resolve(relative);
        if (!Files.isDirectory(dir)) {
            throw ApiException.notFound("file_not_found", "No such folder: " + relative);
        }
        List<JsonObject> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (isHidden(child.normalize())) {
                    continue;
                }
                BasicFileAttributes a;
                try {
                    a = Files.readAttributes(child, BasicFileAttributes.class);
                } catch (IOException e) {
                    continue; // vanished or unreadable
                }
                JsonObject o = new JsonObject();
                o.addProperty("name", child.getFileName().toString());
                o.addProperty("dir", a.isDirectory());
                o.addProperty("size", a.isDirectory() ? 0 : a.size());
                o.addProperty("modified", a.lastModifiedTime().toMillis());
                entries.add(o);
            }
        }
        entries.sort(Comparator.<JsonObject, Boolean>comparing(o -> !o.get("dir").getAsBoolean())
                .thenComparing(o -> o.get("name").getAsString().toLowerCase(Locale.ROOT)));
        JsonArray arr = new JsonArray();
        entries.forEach(arr::add);
        JsonObject o = new JsonObject();
        o.addProperty("path", relative(dir));
        o.add("entries", arr);
        return o;
    }

    /** A text file's content; binary or very large files are refused. */
    public JsonObject read(String relative) throws ApiException, IOException {
        Path file = resolve(relative);
        if (!Files.isRegularFile(file)) {
            throw ApiException.notFound("file_not_found", "No such file: " + relative);
        }
        if (Files.size(file) > MAX_TEXT) {
            throw new ApiException(413, "file_too_large", "The file is too large to edit (max. 1 MB)");
        }
        byte[] bytes = Files.readAllBytes(file);
        String text;
        try {
            for (byte b : bytes) {
                if (b == 0) {
                    throw new CharacterCodingException();
                }
            }
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ApiException(415, "binary_file", "This is not a text file");
        }
        JsonObject o = info(file);
        o.addProperty("content", text);
        return o;
    }

    /** Saves a text file (replacing it in one step, so a lost connection never leaves half a file). */
    public JsonObject write(String relative, String content) throws ApiException, IOException {
        Path file = resolve(relative);
        if (file.equals(root) || Files.isDirectory(file)) {
            throw ApiException.badRequest("bad_path", "Not a file");
        }
        if (!Files.isDirectory(file.getParent())) {
            throw ApiException.notFound("file_not_found", "The folder does not exist");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT) {
            throw new ApiException(413, "file_too_large", "The file is too large to edit (max. 1 MB)");
        }
        Path tmp = Files.createTempFile(file.getParent(), ".remotemc-", ".tmp");
        try {
            Files.write(tmp, bytes);
            move(tmp, file);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return info(file);
    }

    /** Stores an uploaded file (any type, e.g. a plugin JAR), replacing an existing one. */
    public JsonObject upload(String relative, InputStream in, long declaredLength) throws ApiException, IOException {
        Path file = resolve(relative);
        if (file.equals(root) || Files.isDirectory(file) || file.getFileName().toString().isBlank()) {
            throw ApiException.badRequest("bad_path", "Not a file");
        }
        if (!Files.isDirectory(file.getParent())) {
            throw ApiException.notFound("file_not_found", "The folder does not exist");
        }
        if (declaredLength > MAX_UPLOAD) {
            throw new ApiException(413, "file_too_large", "The file is too large (max. 200 MB)");
        }
        Path tmp = Files.createTempFile(file.getParent(), ".remotemc-", ".tmp");
        try {
            long total = 0;
            byte[] buf = new byte[64 * 1024];
            try (OutputStream out = Files.newOutputStream(tmp)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_UPLOAD) {
                        throw new ApiException(413, "file_too_large", "The file is too large (max. 200 MB)");
                    }
                    out.write(buf, 0, n);
                }
            }
            move(tmp, file);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return info(file);
    }

    /**
     * Deletes a file or a folder with everything in it. With a trash it is moved there instead
     * and the id to restore it is returned; null when it was deleted for good.
     */
    public String delete(String relative) throws ApiException, IOException {
        Path p = resolve(relative);
        if (p.equals(root)) {
            throw ApiException.forbidden("The server folder itself cannot be deleted");
        }
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw ApiException.notFound("file_not_found", "No such file: " + relative);
        }
        for (Path h : hidden) {
            if (h.startsWith(p)) {
                throw ApiException.forbidden("This folder contains RemoteMC's own files");
            }
        }
        if (trash != null) {
            purgeTrash(System.currentTimeMillis());
            String id = System.currentTimeMillis() + "-" + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xffffff);
            Path slot = trash.resolve(id);
            Files.createDirectories(slot);
            try {
                Files.move(p, slot.resolve(p.getFileName().toString()));
                Files.writeString(slot.resolve(ORIGIN), relative(p));
                return id;
            } catch (IOException e) {
                // Another drive or the file is in use: fall back to deleting it.
                deleteTree(slot);
            }
        }
        deleteNow(p, relative);
        return null;
    }

    /** Puts a deleted file back where it was; fails if something new is there now. */
    public JsonObject restore(String id) throws ApiException, IOException {
        if (trash == null || id == null || !id.matches("[0-9]{1,20}-[0-9a-f]{1,8}")) {
            throw ApiException.notFound("not_in_trash", "Nothing to restore");
        }
        Path slot = trash.resolve(id);
        Path origin = slot.resolve(ORIGIN);
        if (!Files.isRegularFile(origin)) {
            throw ApiException.notFound("not_in_trash", "Nothing to restore (it may be older than a day)");
        }
        Path target = resolve(Files.readString(origin).strip());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(409, "file_exists", "Something new is there now: " + relative(target));
        }
        Path saved = slot.resolve(target.getFileName().toString());
        Files.createDirectories(target.getParent());
        Files.move(saved, target);
        deleteTree(slot);
        return info(target);
    }

    /** Removes trash entries older than a day. */
    void purgeTrash(long now) {
        if (trash == null) {
            return;
        }
        try (Stream<Path> entries = Files.list(trash)) {
            for (Path slot : (Iterable<Path>) entries::iterator) {
                String name = slot.getFileName().toString();
                long time;
                try {
                    time = Long.parseLong(name.substring(0, Math.max(0, name.indexOf('-'))));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (now - time > TRASH_KEEP_MILLIS) {
                    deleteTree(slot);
                }
            }
        } catch (IOException ignored) {
            // tried again with the next delete
        }
    }

    private static void deleteTree(Path p) {
        try (Stream<Path> walk = Files.walk(p)) {
            for (Path q : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(q);
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

    private void deleteNow(Path p, String relative) throws ApiException, IOException {
        try {
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> walk = Files.walk(p)) {
                    for (Path q : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                        Files.delete(q);
                    }
                }
            } else {
                Files.delete(p);
            }
        } catch (AccessDeniedException | DirectoryNotEmptyException e) {
            throw new ApiException(409, "file_locked", "The file is in use or write-protected: " + e.getFile());
        } catch (FileSystemException e) {
            if (e instanceof NoSuchFileException) {
                throw ApiException.notFound("file_not_found", "No such file: " + relative);
            }
            throw new ApiException(409, "file_locked", "The file is in use: " + e.getReason());
        }
    }

    public JsonObject mkdir(String relative) throws ApiException, IOException {
        Path dir = resolve(relative);
        if (Files.exists(dir)) {
            throw new ApiException(409, "file_exists", "Already exists");
        }
        Files.createDirectories(dir);
        return info(dir);
    }

    /** Server-relative path of [file] (e.g. a plugin's JAR), or null if it is not inside. */
    public String relativeOrNull(Path file) {
        try {
            Path real = file.toRealPath();
            return real.startsWith(root) && !isHidden(real) ? relative(real) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void move(Path from, Path to) throws IOException, ApiException {
        try {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (AccessDeniedException e) {
            throw new ApiException(409, "file_locked", "The file is in use or write-protected");
        } catch (FileSystemException e) {
            throw new ApiException(409, "file_locked", "The file is in use: " + e.getReason());
        }
    }

    private JsonObject info(Path p) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("path", relative(p));
        o.addProperty("name", p.getFileName() == null ? "" : p.getFileName().toString());
        o.addProperty("dir", Files.isDirectory(p));
        o.addProperty("size", Files.isDirectory(p) ? 0 : Files.size(p));
        o.addProperty("modified", Files.getLastModifiedTime(p).toMillis());
        return o;
    }
}
