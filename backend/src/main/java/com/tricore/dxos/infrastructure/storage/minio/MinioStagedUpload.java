package com.tricore.dxos.infrastructure.storage.minio;

import com.tricore.dxos.core.data.*;
import java.io.*;
import java.nio.file.*;

/** Bounded heap; owns only the temporary file, never the caller stream. */
final class MinioStagedUpload implements AutoCloseable {
    private final Path file;
    private MinioStagedUpload(Path file) { this.file = file; }
    Path file() { return file; }
    static MinioStagedUpload create(StoreObjectCommand command, Path directory, long maxBytes) {
        if (command == null || command.metadata().contentLength() > maxBytes) throw failure(StorageErrorCode.INVALID_INPUT);
        Path file = null;
        try {
            boolean posix = directory.getFileSystem().supportedFileAttributeViews().contains("posix");
            if (posix) Files.createDirectories(directory, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
            else Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                throw failure(StorageErrorCode.UNAVAILABLE);
            if (posix && Files.getPosixFilePermissions(directory).stream().anyMatch(p ->
                    p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
                throw failure(StorageErrorCode.UNAVAILABLE);
            file = posix ? Files.createTempFile(directory, "dxos-minio-upload-", ".tmp",
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(directory, "dxos-minio-upload-", ".tmp");
            long count = 0;
            boolean excessive = false;
            try (OutputStream output = Files.newOutputStream(file)) {
                byte[] buffer = new byte[8192];
                while (true) {
                    int n;
                    try {
                        n = command.content().read(buffer);
                        if (n == 0) {
                            int one = command.content().read();
                            if (one == -1) break;
                            buffer[0] = (byte) one; n = 1;
                        }
                    } catch (IOException | RuntimeException error) { throw failure(StorageErrorCode.INVALID_INPUT); }
                    if (n == -1) break;
                    if (n < 0 || n > buffer.length) throw failure(StorageErrorCode.INVALID_INPUT);
                    if (count > Long.MAX_VALUE - n) excessive = true;
                    else count += n;
                    if (count > maxBytes) excessive = true;
                    if (!excessive) output.write(buffer, 0, n);
                }
            }
            if (excessive || count != command.metadata().contentLength()) throw failure(StorageErrorCode.INVALID_INPUT);
            return new MinioStagedUpload(file);
        } catch (IOException | RuntimeException error) {
            if (file != null) try { Files.deleteIfExists(file); } catch (IOException | RuntimeException ignored) { /* sanitized primary failure */ }
            if (error instanceof StorageException normalized) throw normalized;
            throw failure(StorageErrorCode.UNAVAILABLE);
        }
    }
    @Override public void close() {
        try { Files.deleteIfExists(file); } catch (IOException | RuntimeException error) { throw failure(StorageErrorCode.UNAVAILABLE); }
    }
    private static StorageException failure(StorageErrorCode code) { return new StorageException(code); }
}
