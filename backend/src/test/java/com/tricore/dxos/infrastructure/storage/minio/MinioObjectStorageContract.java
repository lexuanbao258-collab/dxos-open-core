package com.tricore.dxos.infrastructure.storage.minio;

import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.tricore.dxos.infrastructure.storage.minio.MinioTestSupport.*;

/** Provider contract mirror; the existing Core contract and its in-memory tests remain untouched. */
abstract class MinioObjectStorageContract {
    abstract ObjectStoragePort storage();
    abstract ObjectKey newKey();
    @Test void storeReadMetadataOverwriteAndDelete() throws Exception {
        ObjectKey key = newKey(); var command = command(key, "original", StoreMode.CREATE_ONLY);
        assertThat(storage().store(command)).isEqualTo(command.metadata());
        assertThat(storage().metadata(key)).isEqualTo(command.metadata());
        try (var object = storage().read(key)) {
            assertThat(object.metadata()).isEqualTo(command.metadata());
            assertThat(object.content().readAllBytes()).isEqualTo("original".getBytes());
        }
        error(() -> storage().store(command(key, "loser", StoreMode.CREATE_ONLY)), StorageErrorCode.CONFLICT);
        assertThat(storage().metadata(key)).isEqualTo(command.metadata());
        var replacement = new ObjectMetadata(key, "application/octet-stream", 1, Map.of());
        storage().store(new StoreObjectCommand(replacement, new ByteArrayInputStream(new byte[]{42}), StoreMode.OVERWRITE));
        try (var object = storage().read(key)) {
            assertThat(object.metadata()).isEqualTo(replacement);
            assertThat(object.content().readAllBytes()).containsExactly((byte)42);
        }
        storage().delete(key);
        error(() -> storage().read(key), StorageErrorCode.NOT_FOUND);
        error(() -> storage().metadata(key), StorageErrorCode.NOT_FOUND);
        error(() -> storage().delete(key), StorageErrorCode.NOT_FOUND);
    }
    @Test void overwriteCreatesAndZeroByteObjectRoundTrips() throws Exception {
        ObjectKey key = newKey(); var m = new ObjectMetadata(key, "text/plain", 0, Map.of("Source", "A", "source", "B"));
        storage().store(new StoreObjectCommand(m, InputStream.nullInputStream(), StoreMode.OVERWRITE));
        try (var object = storage().read(key)) { assertThat(object.metadata()).isEqualTo(m); assertThat(object.content().read()).isEqualTo(-1); }
    }
    @Test void invalidLengthPreservesOldObjectAndCallerOwnsStream() throws Exception {
        ObjectKey key = newKey(); var original = storage().store(command(key, "old", StoreMode.CREATE_ONLY));
        for (long length : new long[]{0, 9}) {
            boolean[] closed = {false};
            var input = new ByteArrayInputStream("new".getBytes()) { public void close() { closed[0] = true; } };
            error(() -> storage().store(new StoreObjectCommand(new ObjectMetadata(key, "text/plain", length, Map.of()), input,
                    StoreMode.OVERWRITE)), StorageErrorCode.INVALID_INPUT);
            assertThat(closed[0]).isFalse(); assertThat(input.available()).isZero();
        }
        assertThat(storage().metadata(key)).isEqualTo(original);
        try (var object = storage().read(key)) { assertThat(object.content().readAllBytes()).isEqualTo("old".getBytes()); }
    }
    @Test void openReadPreservesSnapshotAcrossOverwriteAndDelete() throws Exception {
        ObjectKey key = newKey(); String old = "a".repeat(65536); var original = storage().store(command(key, old, StoreMode.CREATE_ONLY));
        try (var object = storage().read(key)) {
            assertThat(object.content().read()).isEqualTo('a');
            storage().store(command(key, "new", StoreMode.OVERWRITE)); storage().delete(key);
            assertThat(object.metadata()).isEqualTo(original);
            assertThat(object.content().readAllBytes()).isEqualTo(old.substring(1).getBytes());
        }
    }
    @Test void closeIsIdempotentAndClosedStreamIsInvalid() throws Exception {
        ObjectKey key = newKey(); var m = storage().store(command(key, "hello", StoreMode.CREATE_ONLY));
        var resource = storage().read(key); var stream = resource.content();
        assertThat(stream.read()).isEqualTo('h'); stream.close(); resource.close();
        assertThat(resource.metadata()).isEqualTo(m);
        error(resource::content, StorageErrorCode.INVALID_INPUT); error(stream::read, StorageErrorCode.INVALID_INPUT);
    }
}
