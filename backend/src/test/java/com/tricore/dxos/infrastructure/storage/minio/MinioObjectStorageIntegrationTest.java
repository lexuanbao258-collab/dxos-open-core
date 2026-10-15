package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.tricore.dxos.infrastructure.storage.minio.MinioTestSupport.*;

@Tag("minio-real")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(90)
class MinioObjectStorageIntegrationTest extends MinioObjectStorageContract {
    MinioStorageProperties p;
    S3Client client;
    MinioObjectStorageAdapter adapter;
    final List<ObjectKey> keys = new ArrayList<>();
    @BeforeAll void connect() {
        p = MinioProcessWorker.properties();
        client = MinioStorageConfiguration.createClient(p);
        adapter = new MinioObjectStorageAdapter(p, client, new ObjectMapper());
    }
    @AfterAll void close() { if (client != null) client.close(); }
    @AfterEach void cleanupOnlyOwnedObjects() {
        try { for (var key : keys) try { adapter.delete(key); } catch (StorageException e) {
            if (e.code() != StorageErrorCode.NOT_FOUND) throw e;
        } } finally { keys.clear(); }
    }
    @Override ObjectStoragePort storage() { return adapter; }
    @Override ObjectKey newKey() {
        var key = new ObjectKey("integration/ ../Tiếng Việt/" + UUID.randomUUID()); keys.add(key); return key;
    }
    Process worker(String operation, ObjectKey key, String payload) throws IOException {
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        return new ProcessBuilder(executable, "-cp", System.getProperty("minio.test.classpath"),
                MinioProcessWorker.class.getName(), operation, key.value(), payload)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    @Test void concurrentCreatorsAcrossIndependentJvmsHaveExactlyOneWinner() throws Exception {
        for (int repetition = 0; repetition < 5; repetition++) {
            ObjectKey key = newKey(); Process first = worker("create", key, "first"), second = worker("create", key, "second");
            try {
                var a = first.inputReader(); var b = second.inputReader();
                assertThat(a.readLine()).isEqualTo("READY"); assertThat(b.readLine()).isEqualTo("READY");
                first.outputWriter().append("GO\n").flush(); second.outputWriter().append("GO\n").flush();
                assertThat(first.waitFor(35, TimeUnit.SECONDS)).isTrue(); assertThat(second.waitFor(35, TimeUnit.SECONDS)).isTrue();
                String ar = a.readLine(), br = b.readLine(); assertThat(List.of(ar, br)).containsExactlyInAnyOrder("STORED", "CONFLICT");
                String winner = ar.equals("STORED") ? "first" : "second";
                try (var object = adapter.read(key)) {
                    assertThat(object.metadata()).isEqualTo(command(key, winner, StoreMode.CREATE_ONLY).metadata());
                    assertThat(object.content().readAllBytes()).isEqualTo(winner.getBytes());
                }
            } finally { first.destroyForcibly(); second.destroyForcibly(); }
        }
    }
    @Test void concurrentOverwritePublishesWholeContentMetadataPairs() throws Exception {
        ObjectKey key = newKey();
        try (var independent = MinioStorageConfiguration.createClient(p); var pool = Executors.newFixedThreadPool(2)) {
            var other = new MinioObjectStorageAdapter(p, independent, new ObjectMapper()); var barrier = new CyclicBarrier(2);
            var a = pool.submit(() -> { barrier.await(); return adapter.store(command(key, "first", StoreMode.OVERWRITE)); });
            var b = pool.submit(() -> { barrier.await(); return other.store(command(key, "second", StoreMode.OVERWRITE)); });
            a.get(35, TimeUnit.SECONDS); b.get(35, TimeUnit.SECONDS);
            try (var object = adapter.read(key)) {
                String payload = new String(object.content().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertThat(payload).isIn("first", "second");
                assertThat(object.metadata()).isEqualTo(command(key, payload, StoreMode.OVERWRITE).metadata());
            }
        }
    }
    @Test void freshJvmCanReadObjectAfterWriterExits() throws Exception {
        ObjectKey key = newKey(); Process writer = worker("create", key, "persistent");
        try {
            assertThat(writer.inputReader().readLine()).isEqualTo("READY"); writer.outputWriter().append("GO\n").flush();
            assertThat(writer.waitFor(35, TimeUnit.SECONDS)).isTrue(); assertThat(writer.exitValue()).isZero();
            assertThat(writer.inputReader().readLine()).isEqualTo("STORED");
        } finally { writer.destroyForcibly(); }
        Process reader = worker("verify", key, "persistent");
        try { assertThat(reader.waitFor(35, TimeUnit.SECONDS)).isTrue(); assertThat(reader.exitValue()).isZero();
            assertThat(reader.inputReader().readLine()).isEqualTo("VERIFIED"); } finally { reader.destroyForcibly(); }
    }
    @Test void applicationIdentityCannotWriteOutsideItsConfiguredPrefix() {
        String outside = "outside-adapter/" + UUID.randomUUID(); boolean written = false;
        try {
            client.putObject(software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                    .bucket(p.bucket()).key(outside).build(), software.amazon.awssdk.core.sync.RequestBody.empty());
            written = true;
            throw new AssertionError("Application policy permits writes outside the configured namespace");
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            assertThat(e.statusCode()).isEqualTo(403);
        } finally {
            if (written) client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                    .bucket(p.bucket()).key(outside).build());
        }
    }
    @Test void exactCodeUnitsInCustomMetadataRoundTrip() throws Exception {
        ObjectKey key = newKey(); var m = new ObjectMetadata(key, "text/plain; charset=UTF-8", 0,
                Map.of("Source", "A", "source", "B", "unicode", "\uD800\u0000Tiếng Việt", "empty", ""));
        adapter.store(new StoreObjectCommand(m, InputStream.nullInputStream(), StoreMode.CREATE_ONLY));
        assertThat(adapter.metadata(key)).isEqualTo(m);
        try (var object = adapter.read(key)) { assertThat(object.metadata()).isEqualTo(m); assertThat(object.content().read()).isEqualTo(-1); }
    }
    @Test void anonymousReadIsDeniedAndWrongCredentialsAreSanitized() throws Exception {
        ObjectKey key = newKey(); adapter.store(command(key, "private", StoreMode.CREATE_ONLY));
        String physical = new MinioObjectKeyCodec(p.keyPrefix(), p.maxKeyBytes()).encode(key);
        var url = URI.create(p.endpoint().toString().replaceAll("/$", "") + "/" + p.bucket() + "/" + physical);
        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(HttpRequest.newBuilder(url).timeout(java.time.Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(403);
        }
        var bad = new MinioStorageProperties(p.endpoint(), p.region(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                p.bucket(), p.keyPrefix(), p.maxObjectBytes(), p.maxMetadataBytes(), p.maxKeyBytes(), p.stagingDirectory(),
                p.connectTimeout(), p.readTimeout(), p.requestTimeout(), true);
        try (var denied = MinioStorageConfiguration.createClient(bad)) {
            var unauthorized = new MinioObjectStorageAdapter(bad, denied, new ObjectMapper());
            error(() -> unauthorized.read(key), StorageErrorCode.ACCESS_FAILURE);
        }
    }
}
