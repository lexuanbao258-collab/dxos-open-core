package com.tricore.dxos.infrastructure.storage.local;

import com.tricore.dxos.core.data.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Child JVM for local cross-process CREATE_ONLY verification, no external services. */
public final class LocalStorageProcessWorker {
    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            var port = new LocalFileSystemStorageAdapter(Path.of(args[0]), 4096, 4096);
            var key = new ObjectKey("cross-process/creator");
            byte[] bytes = args[1].getBytes(StandardCharsets.UTF_8);
            System.out.println("READY"); System.out.flush();
            if (new BufferedReader(new InputStreamReader(System.in)).readLine() == null) {
                throw new IllegalStateException();
            }
            try {
                port.store(new StoreObjectCommand(
                        new ObjectMetadata(key, "text/plain", bytes.length, Map.of("source", args[1])),
                        new ByteArrayInputStream(bytes), StoreMode.CREATE_ONLY));
                System.out.println("STORED");
            } catch (StorageException failure) {
                System.out.println(failure.code().name());
            }
        } catch (Exception | AssertionError failure) {
            System.out.println("FAILED");
            System.exit(1);
        }
    }
}
