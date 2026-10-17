package com.tricore.dxos.infrastructure.storage.local;

import com.tricore.dxos.core.data.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;

/**
 * Single-host object storage backed by a private local filesystem directory.
 * No cloud, S3 or external storage process is required. A per-key OS file lock
 * serializes writers across JVMs; a pointer rename atomically publishes content
 * and metadata. Reads copy a pinned snapshot before releasing the key lock.
 *
 * This is NOT a distributed storage adapter: every instance needs the same
 * strongly consistent filesystem and properly enforced file locks.
 */
public final class LocalFileSystemStorageAdapter implements ObjectStoragePort {
    private static final int MAGIC = 0x44584f31; // DXO1
    private static final int MAX_KEY_CHARS = 2048;
    private static final int MAX_FIELDS = 128;
    private static final ReentrantLock[] JVM_LOCKS = new ReentrantLock[256];
    static { for (int i = 0; i < JVM_LOCKS.length; i++) JVM_LOCKS[i] = new ReentrantLock(); }

    private final Path root;
    private final long maxObjectBytes;
    private final int maxMetadataBytes;

    public LocalFileSystemStorageAdapter(Path root, long maxObjectBytes, int maxMetadataBytes) {
        if (root == null || maxObjectBytes < 0 || maxMetadataBytes < 128 || maxMetadataBytes > 1_048_576) {
            throw invalid();
        }
        this.root = root.toAbsolutePath().normalize();
        this.maxObjectBytes = maxObjectBytes;
        this.maxMetadataBytes = maxMetadataBytes;
        try {
            ensurePrivateDirectory(this.root);
            ensurePrivateDirectory(this.root.resolve("objects"));
        } catch (IOException | SecurityException e) { throw unavailable(); }
    }

    @Override public ObjectMetadata store(StoreObjectCommand command) {
        if (command == null) throw invalid();
        ObjectMetadata meta = command.metadata();
        validateKey(meta.key());
        byte[] envelope = encode(meta);
        if (meta.contentLength() > maxObjectBytes) throw invalid();
        Path dir = folder(meta.key());
        Path staged = null;
        try {
            staged = Files.createTempFile(dir, ".upload-", ".tmp");
            stage(command.content(), staged, meta.contentLength());
            Path ready = staged;
            return locked(dir, () -> {
                String current = currentId(dir);
                if (command.mode() == StoreMode.CREATE_ONLY && current != null) throw conflict();
                if (current != null) verifyKey(dir, current, meta.key());
                String version = UUID.randomUUID().toString().replace("-", "");
                Path blob = dir.resolve("v-" + version + ".blob");
                Path attributes = dir.resolve("v-" + version + ".meta");
                Path next = null;
                boolean published = false;
                try {
                    Files.move(ready, blob, ATOMIC_MOVE);
                    Files.write(attributes, envelope, CREATE_NEW, WRITE, NOFOLLOW_LINKS);
                    force(attributes);
                    force(blob);
                    // Persist the immutable version and newly created key directories
                    // before publishing the CURRENT pointer on POSIX filesystems.
                    forceStorageDirectories(dir);
                    next = Files.createTempFile(dir, ".pointer-", ".tmp");
                    Files.writeString(next, version, StandardCharsets.US_ASCII, WRITE, TRUNCATE_EXISTING);
                    force(next);
                    Files.move(next, dir.resolve("CURRENT"), ATOMIC_MOVE, REPLACE_EXISTING);
                    published = true;
                    // A durable rename needs a directory sync in addition to file sync.
                    // If this fails, the outcome may be uncertain: do not delete the
                    // newly published version or claim that the operation rolled back.
                    forceDirectory(dir);
                } finally {
                    if (next != null) Files.deleteIfExists(next);
                    if (!published) {
                        Files.deleteIfExists(blob);
                        Files.deleteIfExists(attributes);
                    }
                }
                pruneOldVersions(dir, version);
                return meta;
            });
        } catch (IOException | SecurityException e) { throw unavailable(); }
        finally { if (staged != null) try { Files.deleteIfExists(staged); } catch (IOException ignored) { /* operator cleanup */ } }
    }

    @Override public ObjectContent read(ObjectKey key) {
        validateKey(key);
        Path dir = folder(key);
        return locked(dir, () -> {
            String id = requiredCurrent(dir);
            ObjectMetadata meta = loadMetadata(dir, id);
            if (!meta.key().equals(key)) throw unavailable();
            Path snapshot = Files.createTempFile(dir, ".snapshot-", ".tmp");
            try {
                Path blob = dir.resolve("v-" + id + ".blob");
                long bytes = copyBounded(blob, snapshot, meta.contentLength());
                if (bytes != meta.contentLength()) throw unavailable();
                InputStream input = Files.newInputStream(snapshot, READ, NOFOLLOW_LINKS);
                return new Snapshot(meta, snapshot, input);
            } catch (IOException | RuntimeException e) {
                try { Files.deleteIfExists(snapshot); } catch (IOException ignored) { /* operator cleanup */ }
                if (e instanceof StorageException se) throw se;
                throw unavailable();
            }
        });
    }

    @Override public ObjectMetadata metadata(ObjectKey key) {
        validateKey(key);
        Path dir = folder(key);
        return locked(dir, () -> {
            ObjectMetadata meta = loadMetadata(dir, requiredCurrent(dir));
            if (!meta.key().equals(key)) throw unavailable();
            return meta;
        });
    }

    @Override public void delete(ObjectKey key) {
        validateKey(key);
        Path dir = folder(key);
        locked(dir, () -> {
            String id = requiredCurrent(dir);
            verifyKey(dir, id, key);
            Files.delete(dir.resolve("CURRENT"));
            // Persist the deletion of the namespace entry on POSIX filesystems.
            forceDirectory(dir);
            pruneOldVersions(dir, null);
            return null;
        });
    }

    private void stage(InputStream caller, Path file, long declared) {
        byte[] buffer = new byte[8192];
        long count = 0;
        boolean overflow = false;
        try (OutputStream output = Files.newOutputStream(file, WRITE, NOFOLLOW_LINKS)) {
            while (true) {
                int length;
                try { length = caller.read(buffer); }
                catch (IOException | RuntimeException e) { throw invalid(); }
                if (length < 0) break;
                if (length == 0) continue;
                count += length;
                if (count < 0 || count > maxObjectBytes) overflow = true;
                if (!overflow) output.write(buffer, 0, length);
            }
        } catch (IOException e) { throw unavailable(); }
        if (overflow || count != declared) throw invalid();
    }

    private Path folder(ObjectKey key) {
        byte[] bytes = new byte[key.value().length() * 2];
        for (int i = 0; i < key.value().length(); i++) {
            char c = key.value().charAt(i);
            bytes[2*i] = (byte)(c >>> 8);
            bytes[2*i+1] = (byte)c;
        }
        byte[] digest;
        try { digest = MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw unavailable(); }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) hex.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        String name = hex.toString();
        Path dir = root.resolve("objects").resolve(name.substring(0, 2)).resolve(name);
        try { ensurePrivateDirectory(dir); }
        catch (IOException | SecurityException e) { throw unavailable(); }
        return dir;
    }

    private static void ensurePrivateDirectory(Path path) throws IOException {
        for (Path parent = path; parent != null; parent = parent.getParent()) {
            if (Files.exists(parent, NOFOLLOW_LINKS) && Files.isSymbolicLink(parent)) throw new IOException();
        }
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException concurrentCreator) {
            // Two JVMs can create the same hashed key directory at the same instant.
            // An existing real directory is fine; a symlink or non-directory is not.
            if (!Files.isDirectory(path, NOFOLLOW_LINKS)) throw concurrentCreator;
        }
        if (!Files.isDirectory(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new IOException();
        try {
            // On POSIX, lock down new/existing dedicated storage directories.
            // Windows administrators must protect the configured root with NTFS ACLs.
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException windowsOrOtherNonPosix) {
            // POSIX permissions not available: OS ACLs remain operator-controlled.
        }
    }

    private interface Work<T> { T run() throws IOException; }
    private <T> T locked(Path folder, Work<T> work) {
        int slot = folder.getFileName().toString().hashCode() & 255;
        JVM_LOCKS[slot].lock();
        try (FileChannel channel = FileChannel.open(folder.resolve(".lock"), CREATE, WRITE, NOFOLLOW_LINKS);
             FileLock held = channel.lock()) {
            return work.run();
        } catch (IOException | SecurityException | java.nio.channels.OverlappingFileLockException e) {
            throw unavailable();
        } finally { JVM_LOCKS[slot].unlock(); }
    }

    private static String currentId(Path dir) throws IOException {
        Path marker = dir.resolve("CURRENT");
        if (!Files.exists(marker, NOFOLLOW_LINKS)) return null;
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(marker, READ, NOFOLLOW_LINKS)) {
            bytes = stream.readNBytes(33);
        }
        if (bytes.length != 32) throw unavailable();
        String id = new String(bytes, StandardCharsets.US_ASCII);
        if (!id.matches("[0-9a-f]{32}")) throw unavailable();
        return id;
    }
    private static String requiredCurrent(Path dir) throws IOException {
        String id = currentId(dir);
        if (id == null) throw new StorageException(StorageErrorCode.NOT_FOUND);
        return id;
    }
    private static void verifyKey(Path dir, String id, ObjectKey expected) throws IOException {
        if (!loadMetadata(dir, id).key().equals(expected)) throw unavailable();
    }

    private static ObjectMetadata loadMetadata(Path dir, String id) throws IOException {
        Path file = dir.resolve("v-" + id + ".meta");
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file, READ, NOFOLLOW_LINKS))) {
            if (input.readInt() != MAGIC) throw unavailable();
            String key = readText(input);
            String type = readText(input);
            long length = input.readLong();
            int entries = input.readInt();
            if (length < 0 || entries < 0 || entries > MAX_FIELDS) throw unavailable();
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < entries; i++) {
                String name = readText(input);
                String value = readText(input);
                if (map.put(name, value) != null) throw unavailable();
            }
            if (input.read() != -1) throw unavailable();
            return new ObjectMetadata(new ObjectKey(key), type, length, map);
        } catch (StorageException e) { throw unavailable(); }
    }

    private byte[] encode(ObjectMetadata meta) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(output)) {
                data.writeInt(MAGIC);
                writeText(data, meta.key().value());
                writeText(data, meta.contentType());
                data.writeLong(meta.contentLength());
                if (meta.customMetadata().size() > MAX_FIELDS) throw invalid();
                data.writeInt(meta.customMetadata().size());
                for (var entry : meta.customMetadata().entrySet()) {
                    writeText(data, entry.getKey());
                    writeText(data, entry.getValue());
                    if (output.size() > maxMetadataBytes) throw invalid();
                }
            }
            if (output.size() > maxMetadataBytes) throw invalid();
            return output.toByteArray();
        } catch (IOException e) { throw unavailable(); }
    }
    private static void writeText(DataOutputStream data, String text) throws IOException {
        if ((long) text.length() * 2 > 65536) throw invalid();
        data.writeInt(text.length() * 2);
        for (int i = 0; i < text.length(); i++) data.writeChar(text.charAt(i));
    }
    private static String readText(DataInputStream data) throws IOException {
        int len = data.readInt();
        if (len < 0 || len > 65536 || (len & 1) != 0) throw unavailable();
        char[] chars = new char[len / 2];
        for (int i = 0; i < chars.length; i++) chars[i] = data.readChar();
        return new String(chars);
    }
    private void validateKey(ObjectKey key) {
        if (key == null || key.value().length() > MAX_KEY_CHARS) throw invalid();
    }
    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, WRITE, NOFOLLOW_LINKS)) { channel.force(true); }
    }
    private void forceStorageDirectories(Path dir) throws IOException {
        // Sync from the newest hashed-key directory towards the configured root,
        // so newly created directory entries are persisted before CURRENT changes.
        for (Path path = dir; path != null && path.startsWith(root); path = path.getParent()) {
            forceDirectory(path);
        }
        if (root.getParent() != null) forceDirectory(root.getParent());
    }
    private static void forceDirectory(Path directory) throws IOException {
        // Opening directories for fsync is not portable to Windows. Keep the
        // already-tested Windows behavior but enforce directory sync on POSIX.
        if (!Files.getFileStore(directory).supportsFileAttributeView("posix")) return;
        try (FileChannel channel = FileChannel.open(directory, READ, NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
    private static long copyBounded(Path source, Path target, long length) throws IOException {
        long bytes = 0;
        try (InputStream in = Files.newInputStream(source, READ, NOFOLLOW_LINKS);
             OutputStream out = Files.newOutputStream(target, WRITE, NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                bytes += n;
                if (bytes > length) throw unavailable();
                out.write(buffer, 0, n);
            }
        }
        return bytes;
    }
    private static void pruneOldVersions(Path dir, String keep) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "v-*")) {
            for (Path file : entries) {
                String filename = file.getFileName().toString();
                if (!filename.matches("v-[0-9a-f]{32}[.](blob|meta)")) continue;
                if (keep != null && filename.startsWith("v-" + keep + ".")) continue;
                try { Files.deleteIfExists(file); } catch (IOException ignored) { /* best effort, operator quota */ }
            }
        } catch (IOException ignored) { /* best effort; current data is already committed */ }
    }
    private static StorageException invalid() { return new StorageException(StorageErrorCode.INVALID_INPUT); }
    private static StorageException unavailable() { return new StorageException(StorageErrorCode.UNAVAILABLE); }
    private static StorageException conflict() { return new StorageException(StorageErrorCode.CONFLICT); }

    private static final class Snapshot implements ObjectContent {
        private final ObjectMetadata metadata;
        private final Path snapshot;
        private final InputStream delegate;
        private final InputStream stream;
        private boolean closed;

        private Snapshot(ObjectMetadata metadata, Path snapshot, InputStream delegate) {
            this.metadata = metadata;
            this.snapshot = snapshot;
            this.delegate = delegate;
            this.stream = new InputStream() {
                @Override public int read() {
                    checkOpen();
                    try { return Snapshot.this.delegate.read(); }
                    catch (IOException e) { throw unavailable(); }
                }
                @Override public int read(byte[] bytes, int start, int size) {
                    checkOpen();
                    try { return Snapshot.this.delegate.read(bytes, start, size); }
                    catch (IOException e) { throw unavailable(); }
                }
                @Override public long skip(long count) {
                    checkOpen();
                    try { return Snapshot.this.delegate.skip(count); }
                    catch (IOException e) { throw unavailable(); }
                }
                @Override public int available() {
                    checkOpen();
                    try { return Snapshot.this.delegate.available(); }
                    catch (IOException e) { throw unavailable(); }
                }
                @Override public void close() { Snapshot.this.close(); }
            };
        }
        @Override public ObjectMetadata metadata() { return metadata; }
        @Override public InputStream content() { checkOpen(); return stream; }
        @Override public synchronized void close() {
            if (closed) return;
            closed = true;
            try { delegate.close(); Files.deleteIfExists(snapshot); }
            catch (IOException e) { throw unavailable(); }
        }
        private synchronized void checkOpen() { if (closed) throw invalid(); }
    }
}
