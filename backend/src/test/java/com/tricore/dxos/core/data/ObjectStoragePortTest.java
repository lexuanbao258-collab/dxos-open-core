package com.tricore.dxos.core.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStoragePortTest {
    private static final ObjectKey KEY = new ObjectKey("documents/report");
    private final ObjectStoragePort storage = new InMemoryObjectStorage();

    @Test
    void storesReadsAndLooksUpMatchingMetadata() throws IOException {
        StoreObjectCommand command = command("hello", StoreMode.CREATE_ONLY);
        assertThat(storage.store(command)).isEqualTo(command.metadata());
        assertThat(storage.metadata(KEY)).isEqualTo(command.metadata());
        try (var object = storage.read(KEY)) {
            assertThat(object.metadata()).isEqualTo(command.metadata());
            assertThat(object.content().readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void deletesContentAndMetadataAndReportsMissingObjects() {
        storage.store(command("hello", StoreMode.CREATE_ONLY));
        storage.delete(KEY);
        assertError(() -> storage.read(KEY), StorageErrorCode.NOT_FOUND);
        assertError(() -> storage.metadata(KEY), StorageErrorCode.NOT_FOUND);
        assertError(() -> storage.delete(KEY), StorageErrorCode.NOT_FOUND);
    }

    @Test
    void createOnlyConflictPreservesContentMetadataAndCallerStream() throws IOException {
        ObjectMetadata original = storage.store(command("original", StoreMode.CREATE_ONLY));
        TrackedInput input = new TrackedInput("changed");
        assertError(() -> storage.store(new StoreObjectCommand(metadata(7), input, StoreMode.CREATE_ONLY)),
                StorageErrorCode.CONFLICT);
        assertThat(input.closed).isFalse();
        assertThat(storage.metadata(KEY)).isEqualTo(original);
        try (var object = storage.read(KEY)) {
            assertThat(object.content().readAllBytes()).isEqualTo("original".getBytes(StandardCharsets.UTF_8));
        }
        input.close();
    }

    @Test
    void overwriteCreatesOrReplacesContentAndAllMetadata() throws IOException {
        storage.store(command("first", StoreMode.OVERWRITE));
        ObjectMetadata replacement = new ObjectMetadata(KEY, "application/octet-stream", 1, Map.of());
        storage.store(new StoreObjectCommand(replacement, new ByteArrayInputStream(new byte[]{42}), StoreMode.OVERWRITE));
        assertThat(storage.metadata(KEY)).isEqualTo(replacement);
        try (var object = storage.read(KEY)) {
            assertThat(object.metadata()).isEqualTo(replacement);
            assertThat(object.content().readAllBytes()).containsExactly((byte) 42);
        }
    }

    @Test
    void rejectsInvalidKeysMetadataCommandsAndPortInputs() {
        for (String key : new String[]{null, "", "  "}) {
            assertError(() -> new ObjectKey(key), StorageErrorCode.INVALID_INPUT);
        }
        assertError(() -> new ObjectMetadata(null, "text/plain", 0, Map.of()), StorageErrorCode.INVALID_INPUT);
        assertError(() -> new ObjectMetadata(KEY, null, 0, Map.of()), StorageErrorCode.INVALID_INPUT);
        assertError(() -> new ObjectMetadata(KEY, " ", 0, Map.of()), StorageErrorCode.INVALID_INPUT);
        assertError(() -> metadata(-1), StorageErrorCode.INVALID_INPUT);
        assertError(() -> new ObjectMetadata(KEY, "text/plain", 0, null), StorageErrorCode.INVALID_INPUT);
        assertError(() -> new ObjectMetadata(KEY, "text/plain", 0, Map.of(" ", "x")), StorageErrorCode.INVALID_INPUT);
        Map<String, String> invalid = new HashMap<>();
        invalid.put("name", null);
        assertError(() -> new ObjectMetadata(KEY, "text/plain", 0, invalid), StorageErrorCode.INVALID_INPUT);
        invalid.clear();
        invalid.put(null, "value");
        assertError(() -> new ObjectMetadata(KEY, "text/plain", 0, invalid), StorageErrorCode.INVALID_INPUT);
        assertError(() -> new StoreObjectCommand(null, InputStream.nullInputStream(), StoreMode.CREATE_ONLY),
                StorageErrorCode.INVALID_INPUT);
        assertError(() -> new StoreObjectCommand(metadata(0), null, StoreMode.CREATE_ONLY), StorageErrorCode.INVALID_INPUT);
        assertError(() -> new StoreObjectCommand(metadata(0), InputStream.nullInputStream(), null), StorageErrorCode.INVALID_INPUT);
        assertError(() -> storage.store(null), StorageErrorCode.INVALID_INPUT);
        assertError(() -> storage.read(null), StorageErrorCode.INVALID_INPUT);
        assertError(() -> storage.metadata(null), StorageErrorCode.INVALID_INPUT);
        assertError(() -> storage.delete(null), StorageErrorCode.INVALID_INPUT);
    }

    @Test
    void rejectsLengthMismatchWithoutClosingCallerInput() throws IOException {
        for (long length : new long[]{0, 99}) {
            TrackedInput input = new TrackedInput("replacement");
            assertError(() -> storage.store(new StoreObjectCommand(metadata(length), input, StoreMode.OVERWRITE)),
                    StorageErrorCode.INVALID_INPUT);
            assertThat(input.closed).isFalse();
            input.close();
        }
    }

    @Test
    void callerClosesInputAndReadResourceReleasesStreamIdempotently() throws IOException {
        TrackedInput input = new TrackedInput("hello");
        storage.store(new StoreObjectCommand(metadata(5), input, StoreMode.CREATE_ONLY));
        assertThat(input.closed).isFalse();
        input.close();
        assertThat(input.closed).isTrue();
        ObjectContent resource = storage.read(KEY);
        InputStream stream;
        try (resource) {
            stream = resource.content();
            assertThat(stream.read()).isEqualTo('h');
        }
        resource.close();
        assertThat(resource.metadata()).isEqualTo(metadata(5));
        assertError(() -> resource.content(), StorageErrorCode.INVALID_INPUT);
        assertError(() -> stream.read(), StorageErrorCode.INVALID_INPUT);
        try (var another = storage.read(KEY)) {
            another.content().close();
            assertError(() -> another.content(), StorageErrorCode.INVALID_INPUT);
        }
    }

    @Test
    void readSnapshotKeepsContentAndMetadataTogetherAcrossOverwriteAndDelete() throws IOException {
        ObjectMetadata original = storage.store(command("old", StoreMode.CREATE_ONLY));
        try (var object = storage.read(KEY)) {
            storage.store(command("new content", StoreMode.OVERWRITE));
            storage.delete(KEY);
            assertThat(object.metadata()).isEqualTo(original);
            assertThat(object.content().readAllBytes()).isEqualTo("old".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void zeroLengthContentAndOpaqueKeysAndDefensiveMetadataAreSupported() {
        assertThat(new ObjectKey(" Mixed/Key ").value()).isEqualTo(" Mixed/Key ");
        Map<String, String> custom = new HashMap<>(Map.of("name", "original"));
        ObjectMetadata metadata = new ObjectMetadata(KEY, "application/octet-stream", 0, custom);
        custom.put("name", "changed");
        assertThat(metadata.customMetadata()).containsEntry("name", "original");
        assertThatThrownBy(() -> metadata.customMetadata().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(storage.store(new StoreObjectCommand(metadata, InputStream.nullInputStream(), StoreMode.CREATE_ONLY)))
                .isEqualTo(metadata);
    }

    @Test
    void normalizedErrorsExposeOnlyStableCodes() {
        for (StorageErrorCode code : StorageErrorCode.values()) {
            StorageException error = new StorageException(code);
            error.addSuppressed(new IOException("provider secret"));
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.getMessage()).isEqualTo(code.name());
            assertThat(error.getCause()).isNull();
            assertThat(error.getStackTrace()).isEmpty();
            assertThat(error.getSuppressed()).isEmpty();
        }
    }

    @Test
    void inputReadFailureIsSanitizedWithoutTakingOwnership() {
        boolean[] closed = {false};
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("sensitive input details");
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };
        assertError(() -> storage.store(new StoreObjectCommand(metadata(1), broken, StoreMode.CREATE_ONLY)),
                StorageErrorCode.INVALID_INPUT);
        assertThat(closed[0]).isFalse();
        assertError(() -> storage.metadata(KEY), StorageErrorCode.NOT_FOUND);
    }

    @Test
    void consumesFromCurrentPositionAndClosesReadResourceAfterConsumerFailure() throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream("prefixhello".getBytes(StandardCharsets.UTF_8));
        assertThat(input.skip(6)).isEqualTo(6);
        storage.store(new StoreObjectCommand(metadata(5), input, StoreMode.CREATE_ONLY));
        ObjectContent resource = storage.read(KEY);
        InputStream stream = resource.content();
        assertThatThrownBy(() -> {
            try (resource) {
                assertThat(stream.read()).isEqualTo('h');
                throw new IllegalStateException("consumer failure");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertError(() -> stream.read(), StorageErrorCode.INVALID_INPUT);
    }

    @Test
    void concurrentCreateOnlyWritersHaveExactlyOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> createAfterSignal(start));
            var second = executor.submit(() -> createAfterSignal(start));
            start.countDown();
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("STORED", "CONFLICT");
            assertThat(storage.metadata(KEY)).isEqualTo(metadata(5));
        } finally {
            executor.shutdownNow();
        }
    }

    private String createAfterSignal(CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start signal timed out");
        }
        try {
            storage.store(command("hello", StoreMode.CREATE_ONLY));
            return "STORED";
        } catch (StorageException error) {
            return error.code().name();
        }
    }

    private static ObjectMetadata metadata(long length) {
        return new ObjectMetadata(KEY, "text/plain", length, Map.of("source", "test"));
    }

    private static StoreObjectCommand command(String content, StoreMode mode) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new StoreObjectCommand(metadata(bytes.length), new ByteArrayInputStream(bytes), mode);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, StorageErrorCode code) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(StorageException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }

    private static final class TrackedInput extends ByteArrayInputStream {
        private boolean closed;

        private TrackedInput(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
