package com.tricore.dxos.infrastructure.storage.minio;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import com.tricore.dxos.core.data.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class MinioTestSupport {
    private MinioTestSupport() {}
    static MinioStorageProperties properties(Path directory) {
        // Ephemeral test-only credentials, unrelated to any running service.
        return new MinioStorageProperties(URI.create("http://127.0.0.1:9000"), "us-east-1",
                java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString(),
                "test-objects", "tests", 67108864L, 1800, 1024, directory,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), true);
    }
    static StoreObjectCommand command(ObjectKey key, String text, StoreMode mode) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new StoreObjectCommand(new ObjectMetadata(key, "text/plain; charset=utf-8", bytes.length,
                Map.of("Source", text, "source", "Tiếng Việt", "empty", "")), new ByteArrayInputStream(bytes), mode);
    }
    static void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, StorageErrorCode code) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action).isInstanceOfSatisfying(StorageException.class, e -> {
            org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(code);
            org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo(code.name());
            org.assertj.core.api.Assertions.assertThat(e.getCause()).isNull();
            org.assertj.core.api.Assertions.assertThat(e.getSuppressed()).isEmpty();
            org.assertj.core.api.Assertions.assertThat(e.getStackTrace()).isEmpty();
        });
    }
}
