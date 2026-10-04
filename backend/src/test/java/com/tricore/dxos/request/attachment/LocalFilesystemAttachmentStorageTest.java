package com.tricore.dxos.request.attachment;

import com.tricore.dxos.request.attachment.storage.LocalFilesystemAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LocalFilesystemAttachmentStorageTest {
    @TempDir Path root;
    private final String key = "requests/" + UUID.randomUUID() + "/" + UUID.randomUUID();

    @Test
    void storesBytesUnderGeneratedKeyAndDeletesIdempotently() throws Exception {
        var storage = new LocalFilesystemAttachmentStorage(root);
        storage.store(key, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertThat(Files.readAllBytes(root.resolve(key))).containsExactly(1, 2, 3);
        storage.delete(key);
        storage.delete(key);
        assertThat(root.resolve(key)).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../secret.txt", "/secret.txt", "requests/../secret.txt", "requests\\file", "requests/invalid/key"})
    void refusesUncontrolledKeysForStoreAndDelete(String invalid) {
        var storage = new LocalFilesystemAttachmentStorage(root);
        assertThatThrownBy(() -> storage.store(invalid, new ByteArrayInputStream(new byte[]{1}))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.delete(invalid)).isInstanceOf(IOException.class);
    }

    @Test
    void refusesOverwriteAndPreservesExistingFile() throws Exception {
        var storage = new LocalFilesystemAttachmentStorage(root);
        storage.store(key, new ByteArrayInputStream(new byte[]{1}));
        assertThatThrownBy(() -> storage.store(key, new ByteArrayInputStream(new byte[]{2}))).isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(root.resolve(key))).containsExactly(1);
    }

    @Test
    void removesPartialFileWhenCopyFails() {
        var storage = new LocalFilesystemAttachmentStorage(root);
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("copy failed"); }
        };
        assertThatThrownBy(() -> storage.store(key, broken)).isInstanceOf(IOException.class);
        assertThat(root.resolve(key)).doesNotExist();
    }

    @Test
    void refusesNonDirectoryInStorageHierarchy() throws Exception {
        Files.writeString(root.resolve("requests"), "unrelated file");
        var storage = new LocalFilesystemAttachmentStorage(root);
        assertThatThrownBy(() -> storage.store(key, new ByteArrayInputStream(new byte[]{1}))).isInstanceOf(IOException.class);
        assertThat(Files.readString(root.resolve("requests"))).isEqualTo("unrelated file");
    }
}
