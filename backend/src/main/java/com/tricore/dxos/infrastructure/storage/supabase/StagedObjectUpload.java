package com.tricore.dxos.infrastructure.storage.supabase;

import com.tricore.dxos.core.data.*;
import java.io.*;
import java.nio.file.*;

/** Bounded heap; owns only the temporary file, never the caller stream. */
final class StagedObjectUpload implements AutoCloseable {
    private final Path file;
    private StagedObjectUpload(Path file) { this.file = file; }
    Path file() { return file; }
    static StagedObjectUpload create(StoreObjectCommand command, Path directory, long maxBytes) {
        if (command == null || command.metadata().contentLength() > maxBytes) throw failure(StorageErrorCode.INVALID_INPUT);
        Path file = null;
        try {
            Files.createDirectories(directory);
            file = Files.createTempFile(directory, "dxos-upload-", ".tmp");
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
            return new StagedObjectUpload(file);
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
