package com.tricore.dxos.request.attachment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

public class LocalFilesystemAttachmentStorage implements AttachmentStorage {
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern KEY_PATTERN = Pattern.compile("requests/" + UUID_PATTERN + "/" + UUID_PATTERN);
    private final Path root;

    public LocalFilesystemAttachmentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(root);
        requireDirectory(root);
        ensureDirectory(target.getParent().getParent());
        ensureDirectory(target.getParent());
        boolean created = false;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            content.transferTo(output);
        } catch (IOException failure) {
            if (created) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Path target = resolve(key);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        requireDirectory(root);
        Path directory = target.getParent().getParent();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        requireDirectory(directory);
        if (!Files.exists(target.getParent(), LinkOption.NOFOLLOW_LINKS)) return;
        requireDirectory(target.getParent());
        if (Files.isSymbolicLink(target)) throw new IOException("Invalid attachment storage entry");
        Files.deleteIfExists(target);
    }

    private Path resolve(String key) throws IOException {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IOException("Invalid attachment storage key");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IOException("Invalid attachment storage key");
        return target;
    }

    private void ensureDirectory(Path directory) throws IOException {
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException ignored) {
            // Concurrent uploads may have already created this directory.
        }
        requireDirectory(directory);
    }

    private void requireDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid attachment storage directory");
        }
    }
}
