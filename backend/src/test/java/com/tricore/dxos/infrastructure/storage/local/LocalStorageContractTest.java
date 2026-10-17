package com.tricore.dxos.infrastructure.storage.local;

import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class LocalStorageContractTest {
    @TempDir Path temp;
    private static final ObjectKey KEY = new ObjectKey("dir/../Mixed á😀 ");

    private LocalFileSystemStorageAdapter store() {
        return new LocalFileSystemStorageAdapter(temp.resolve("objects"), 1024 * 1024, 16384);
    }
    private static StoreObjectCommand command(ObjectKey key, String value, StoreMode mode) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new StoreObjectCommand(new ObjectMetadata(key, "text/plain", bytes.length,
                Map.of("Source", "á", "source", "", "x", value)), new ByteArrayInputStream(bytes), mode);
    }
    private static void error(Runnable task, StorageErrorCode code) {
        assertThatThrownBy(task::run).isInstanceOfSatisfying(StorageException.class, ex -> {
            assertThat(ex.code()).isEqualTo(code);
            assertThat(ex.getMessage()).isEqualTo(code.name());
            assertThat(ex.getCause()).isNull();
            assertThat(ex.getSuppressed()).isEmpty();
            assertThat(ex.getStackTrace()).isEmpty();
        });
    }

    @Test void roundTripMetadataAndSurviveFreshAdapterRestart() throws Exception {
        var first = store();
        var command = command(KEY, "hello á 😀", StoreMode.CREATE_ONLY);
        assertThat(first.store(command)).isEqualTo(command.metadata());
        var newProcessEquivalent = store();
        assertThat(newProcessEquivalent.metadata(KEY)).isEqualTo(command.metadata());
        try (var read = newProcessEquivalent.read(KEY)) {
            assertThat(read.metadata()).isEqualTo(command.metadata());
            assertThat(read.content().readAllBytes()).isEqualTo("hello á 😀".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test void createOnlyConflictPreservesOriginalAndDoesNotCloseCaller() throws Exception {
        var port = store();
        var original = command(KEY, "original", StoreMode.CREATE_ONLY);
        port.store(original);
        var input = new ByteArrayInputStream("loser".getBytes());
        error(() -> port.store(new StoreObjectCommand(command(KEY,"loser",StoreMode.CREATE_ONLY).metadata(),
                input, StoreMode.CREATE_ONLY)), StorageErrorCode.CONFLICT);
        assertThat(input.available()).isGreaterThanOrEqualTo(0);
        assertThat(port.metadata(KEY)).isEqualTo(original.metadata());
        try (var read = port.read(KEY)) { assertThat(read.content().readAllBytes()).isEqualTo("original".getBytes()); }
    }

    @Test void overwriteReplacesContentAndAllMetadataAndZeroByteWorks() throws Exception {
        var port = store();
        port.store(command(KEY, "first", StoreMode.CREATE_ONLY));
        var replacement = new ObjectMetadata(KEY, "application/octet-stream", 1, Map.of());
        port.store(new StoreObjectCommand(replacement, new ByteArrayInputStream(new byte[]{42}), StoreMode.OVERWRITE));
        assertThat(port.metadata(KEY)).isEqualTo(replacement);
        try (var read = port.read(KEY)) { assertThat(read.content().readAllBytes()).containsExactly((byte)42); }
        var zero = command(KEY, "", StoreMode.OVERWRITE);
        port.store(zero);
        try (var read = port.read(KEY)) { assertThat(read.content().readAllBytes()).isEmpty(); }
    }

    @Test void readSnapshotSurvivesOverwriteAndDeletionAndCloseIsIdempotent() throws Exception {
        var port = store();
        var original = command(KEY, "old content", StoreMode.CREATE_ONLY);
        port.store(original);
        var read = port.read(KEY);
        var bytes = read.content();
        port.store(command(KEY, "replacement", StoreMode.OVERWRITE));
        port.delete(KEY);
        assertThat(read.metadata()).isEqualTo(original.metadata());
        assertThat(bytes.readAllBytes()).isEqualTo("old content".getBytes());
        read.close(); read.close();
        error(() -> read.content(), StorageErrorCode.INVALID_INPUT);
        error(() -> { try { bytes.read(); } catch (IOException ex) { throw new RuntimeException(ex); } }, StorageErrorCode.INVALID_INPUT);
        error(() -> port.delete(KEY), StorageErrorCode.NOT_FOUND);
        error(() -> port.metadata(KEY), StorageErrorCode.NOT_FOUND);
        error(() -> port.read(KEY), StorageErrorCode.NOT_FOUND);
    }

    @Test void rejectsLengthMismatchAndCallerReadFailureWithoutReplacingOriginal() throws Exception {
        var port = store();
        port.store(command(KEY, "original", StoreMode.CREATE_ONLY));
        var shortMeta = new ObjectMetadata(KEY, "text/plain", 1, Map.of());
        error(() -> port.store(new StoreObjectCommand(shortMeta, new ByteArrayInputStream("wrong".getBytes()), StoreMode.OVERWRITE)),
                StorageErrorCode.INVALID_INPUT);
        var broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("private input details"); }
        };
        error(() -> port.store(new StoreObjectCommand(shortMeta, broken, StoreMode.OVERWRITE)), StorageErrorCode.INVALID_INPUT);
        assertThat(port.metadata(KEY).contentLength()).isEqualTo(8);
    }

    @Test void supportsIsolatedSurrogatesAndDifferentOpaqueKeys() throws Exception {
        var port = store();
        var high = new ObjectKey("/../" + (char)0xd800);
        var low = new ObjectKey("/../" + (char)0xdc00);
        port.store(command(high, "A", StoreMode.CREATE_ONLY));
        port.store(command(low, "B", StoreMode.CREATE_ONLY));
        assertThat(port.metadata(high).key()).isEqualTo(high);
        assertThat(port.metadata(low).key()).isEqualTo(low);
    }

    @Test void concurrentCreatorInstancesHaveOneWinner() throws Exception {
        var one = store(); var two = store();
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch go = new CountDownLatch(1);
        try {
            Future<String> first = threads.submit(() -> create(one, ready, go, "alpha"));
            Future<String> second = threads.submit(() -> create(two, ready, go, "bravo"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("STORED", "CONFLICT");
        } finally { threads.shutdownNow(); }
    }
    private static String create(ObjectStoragePort port, CountDownLatch ready, CountDownLatch go, String value) throws Exception {
        ready.countDown(); if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException();
        try { port.store(command(KEY, value, StoreMode.CREATE_ONLY)); return "STORED"; }
        catch (StorageException ex) { return ex.code().name(); }
    }

    @Test void independentJvmCreateOnlyHasExactlyOneWinner() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String cp = System.getProperty("localfs.test.runtimeClasspath");
        assertThat(cp).as("Run this test through backend Gradle test task").isNotBlank();
        Path root = temp.resolve("cross-process");
        Process first = null;
        Process second = null;
        ExecutorService readers = Executors.newFixedThreadPool(2);
        try {
            first = new ProcessBuilder(java, "-cp", cp, LocalStorageProcessWorker.class.getName(),
                    root.toString(), "winner-a").redirectErrorStream(true).start();
            second = new ProcessBuilder(java, "-cp", cp, LocalStorageProcessWorker.class.getName(),
                    root.toString(), "winner-b").redirectErrorStream(true).start();
            var a = new BufferedReader(new InputStreamReader(first.getInputStream()));
            var b = new BufferedReader(new InputStreamReader(second.getInputStream()));
            assertThat(readers.submit(a::readLine).get(15, TimeUnit.SECONDS)).isEqualTo("READY");
            assertThat(readers.submit(b::readLine).get(15, TimeUnit.SECONDS)).isEqualTo("READY");
            first.getOutputStream().write(10); first.getOutputStream().flush();
            second.getOutputStream().write(10); second.getOutputStream().flush();
            var ra = readers.submit(a::readLine).get(15, TimeUnit.SECONDS);
            var rb = readers.submit(b::readLine).get(15, TimeUnit.SECONDS);
            assertThat(java.util.List.of(ra, rb)).containsExactlyInAnyOrder("STORED", "CONFLICT");
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isZero();
            assertThat(second.exitValue()).isZero();
            var verifier = new LocalFileSystemStorageAdapter(root, 4096, 4096);
            var persisted = verifier.metadata(new ObjectKey("cross-process/creator"));
            assertThat(persisted.customMetadata().get("source")).isIn("winner-a", "winner-b");
        } finally {
            if (first != null && first.isAlive()) first.destroyForcibly();
            if (second != null && second.isAlive()) second.destroyForcibly();
            if (first != null) first.waitFor(10, TimeUnit.SECONDS);
            if (second != null) second.waitFor(10, TimeUnit.SECONDS);
            readers.shutdownNow();
        }
    }

    @Test void symlinkedRootFailsClosed() throws Exception {
        Path outside = temp.resolve("outside"); Files.createDirectory(outside);
        Path redirect = temp.resolve("link");
        try { Files.createSymbolicLink(redirect, outside); }
        catch (UnsupportedOperationException | IOException | SecurityException unsupported) { return; }
        error(() -> new LocalFileSystemStorageAdapter(redirect, 1024, 1024), StorageErrorCode.UNAVAILABLE);
    }

    @Test void configRequiresNoCloudService() {
        new ApplicationContextRunner().withUserConfiguration(LocalObjectStorageConfiguration.class)
                .withPropertyValues("dxos.object-storage.local.root=" + temp.resolve("boot"))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ObjectStoragePort.class);
                    assertThat(ctx.getBean(ObjectStoragePort.class)).isInstanceOf(LocalFileSystemStorageAdapter.class);
                });
    }

    @Test void configCanDisableLocalForAnotherProvider() {
        new ApplicationContextRunner().withUserConfiguration(LocalObjectStorageConfiguration.class)
                .withPropertyValues("dxos.object-storage.provider=supabase")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ObjectStoragePort.class));
    }
}
