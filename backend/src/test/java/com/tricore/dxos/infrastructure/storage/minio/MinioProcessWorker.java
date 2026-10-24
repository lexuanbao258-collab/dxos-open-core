package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import static com.tricore.dxos.infrastructure.storage.minio.MinioTestSupport.command;

/** Credentials are inherited through environment, never passed in process arguments or output. */
public final class MinioProcessWorker {
    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing local MinIO test configuration");
        return value;
    }
    static MinioStorageProperties properties() {
        URI endpoint = URI.create(required("MINIO_TEST_ENDPOINT"));
        if (!java.util.Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(endpoint.getHost()))
            throw new IllegalStateException("MinIO integration tests require a loopback endpoint");
        return new MinioStorageProperties(endpoint, "us-east-1", required("MINIO_TEST_ACCESS_KEY"), required("MINIO_TEST_SECRET_KEY"),
                required("MINIO_TEST_BUCKET"), required("MINIO_TEST_KEY_PREFIX"), 1048576L, 1800, 1024,
                Path.of(required("MINIO_TEST_STAGING_DIRECTORY")), Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(30), true);
    }
    public static void main(String[] args) {
        try (var client = MinioStorageConfiguration.createClient(properties())) {
            var adapter = new MinioObjectStorageAdapter(properties(), client, new ObjectMapper());
            var key = new ObjectKey(args[1]);
            if (args[0].equals("create")) {
                System.out.println("READY");
                if (!"GO".equals(new BufferedReader(new InputStreamReader(System.in)).readLine())) System.exit(2);
                adapter.store(command(key, args[2], StoreMode.CREATE_ONLY)); System.out.println("STORED");
            } else if (args[0].equals("verify")) {
                try (var object = adapter.read(key)) {
                    if (!java.util.Arrays.equals(object.content().readAllBytes(), args[2].getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        System.exit(3);
                }
                System.out.println("VERIFIED");
            } else throw new IllegalArgumentException();
        } catch (StorageException e) { System.out.println(e.code().name()); System.exit(e.code() == StorageErrorCode.CONFLICT ? 0 : 1); }
        catch (Exception e) { System.out.println("WORKER_FAILED"); System.exit(1); }
    }
}
