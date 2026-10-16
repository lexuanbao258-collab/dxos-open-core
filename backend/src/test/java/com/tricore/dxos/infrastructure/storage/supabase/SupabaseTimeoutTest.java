package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** Raw sockets make partial bodies and client cancellation observable without an unbounded server wait. */
@Timeout(10)
class SupabaseTimeoutTest {
    @TempDir Path temp;
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectKey key = new ObjectKey("timeout");
    final String version = UUID.randomUUID().toString();
    final AtomicInteger requests = new AtomicInteger();
    final AtomicReference<String> providerState = new AtomicReference<>("old");
    final CountDownLatch released = new CountDownLatch(1);
    final List<Socket> sockets = new CopyOnWriteArrayList<>();
    final ExecutorService serverThreads = Executors.newFixedThreadPool(2);
    ServerSocket server;
    HttpClient http;
    SupabaseRestStorageClient client;
    SupabaseObjectStorageAdapter port;
    Future<?> serving;

    void start(String operation, int status) throws Exception {
        if (operation.equals("create")) providerState.set(null);
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        server.setSoTimeout(4000);
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        var properties = new SupabaseStorageProperties(URI.create("https://storage.invalid/storage/v1"),
                "test-key", "secret", "test-bucket", "timeout", operation.equals("lazy-large") ? 1_000_000L : 1024L, 4096, 1024, 16384,
                temp, Duration.ofSeconds(1), Duration.ofMillis(500));
        client = new SupabaseRestStorageClient(properties, mapper, (request, handler) -> {
            var target = URI.create("http://localhost:" + server.getLocalPort() + request.uri().getRawPath()
                    + (request.uri().getRawQuery() == null ? "" : "?" + request.uri().getRawQuery()));
            var builder = HttpRequest.newBuilder(target).timeout(properties.requestTimeout());
            request.headers().map().forEach((name, values) -> values.forEach(v -> builder.header(name, v)));
            return http.send(builder.method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build(), handler);
        });
        port = new SupabaseObjectStorageAdapter(properties, client, mapper);
        serving = serverThreads.submit(() -> {
            try {
                int total = operation.startsWith("lazy") ? 3 : 1;
                for (int i = 0; i < total; i++) {
                    var socket = server.accept(); sockets.add(socket); socket.setSoTimeout(4000);
                    var in = new BufferedInputStream(socket.getInputStream());
                    String first = line(in); int length = 0;
                    for (String header; !(header = line(in)).isEmpty();) {
                        if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(header.substring(15).strip());
                    }
                    if (in.readNBytes(length).length != length) throw new IOException("Truncated test request");
                    requests.incrementAndGet();
                    if (first.contains("/info/") && operation.startsWith("lazy")) {
                        String physical = new SupabaseObjectKeyCodec("timeout", 1024).encode(key);
                        var metadata = mapper.readTree(Base64.getDecoder().decode(new SupabaseMetadataCodec(mapper, 4096)
                                .encode(SupabaseRealSupport.command(key, "hello", StoreMode.CREATE_ONLY).metadata())));
                        var node = mapper.createObjectNode().put("name", physical).put("version", version)
                                .put("bucket_id", "test-bucket").put("size", operation.equals("lazy-large") ? 600_000 : 5).put("etag", version);
                        node.set("metadata", metadata);
                        byte[] bytes = node.toString().getBytes(StandardCharsets.UTF_8);
                        write(socket, 200, bytes.length, bytes); socket.close(); continue;
                    }
                    // Publication precedes acknowledgement: UNAVAILABLE cannot imply rollback.
                    if (first.startsWith("POST")) providerState.set("new");
                    if (first.startsWith("DELETE")) providerState.set(null);
                    byte[] partial = operation.equals("lazy-large") ? SupabaseResponseBodyTest.pattern(300_000, 0)
                            : operation.startsWith("lazy") ? new byte[]{'{'}
                            : ("{\"code\":\"" + (status == 409 ? "KeyAlreadyExists" : "NoSuchKey") + "\",")
                                    .getBytes(StandardCharsets.UTF_8);
                    write(socket, status, operation.equals("lazy-large") ? 600_000 : operation.startsWith("lazy") ? 5 : 100, partial);
                    // No more response bytes. EOF proves cancellation released the underlying connection.
                    if (in.read() != -1) throw new IOException("Unexpected client bytes");
                    released.countDown(); socket.close();
                }
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
    static String line(InputStream in) throws IOException {
        var result = new StringBuilder();
        for (int c; (c = in.read()) != -1;) { if (c == '\n') return result.toString().stripTrailing(); result.append((char)c); if (result.length() > 16384) throw new IOException("Oversized test header"); }
        throw new EOFException();
    }
    void write(Socket socket, int status, int length, byte[] bytes) throws IOException {
        var out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + status + " Test\r\nContent-Length: " + length + "\r\nETag: " + version + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes); out.flush();
    }
    void verify(Runnable action, int count) throws Exception {
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> SupabaseStorageTest.error(action, StorageErrorCode.UNAVAILABLE));
        assertThat(released.await(2, TimeUnit.SECONDS)).as("client cancelled socket").isTrue();
        serving.get(2, TimeUnit.SECONDS);
        assertThat(requests.get()).as("no automatic retry").isEqualTo(count);
        try (var files = Files.list(temp)) { assertThat(files.count()).isZero(); }
    }
    @ParameterizedTest @ValueSource(ints = {200, 503, 409, 404, 403})
    void metadataAndErrorBodiesCannotOutliveDeadlineOrConfirmProviderResults(int status) throws Exception {
        start("metadata", status); verify(() -> port.metadata(key), 1);
        assertThat(providerState.get()).isEqualTo("old");
    }
    @ParameterizedTest @ValueSource(strings = {"create", "overwrite", "delete"})
    void publicationWithLostAcknowledgementIsUnknownOutcomeWithoutRetry(String operation) throws Exception {
        start(operation, 200);
        verify(() -> {
            if (operation.equals("delete")) port.delete(key);
            else port.store(SupabaseRealSupport.command(key, "new", operation.equals("create") ? StoreMode.CREATE_ONLY : StoreMode.OVERWRITE));
        }, 1);
        assertThat(providerState.get()).as("provider committed despite caller UNAVAILABLE; no guaranteed rollback")
                .isEqualTo(operation.equals("delete") ? null : "new");
    }
    @Test void lazyPartialDownloadExpiresAndCancels() throws Exception {
        start("lazy", 200);
        try (var content = port.read(key)) {
            verify(() -> { try { content.content().readAllBytes(); } catch (IOException error) { throw new AssertionError(error); } }, 3);
            content.close();
        }
    }
    @Test void idleLazyHandleExpiresBeforeFirstRead() throws Exception {
        start("lazy-idle", 200);
        try (var content = port.read(key)) {
            assertThat(released.await(2, TimeUnit.SECONDS)).isTrue();
            verify(() -> { try { content.content().read(); } catch (IOException error) { throw new AssertionError(error); } }, 3);
        }
    }
    @Test void largePartialDownloadExpiresAndCancelsSocket() throws Exception {
        start("lazy-large", 200);
        try (var content = port.read(key)) {
            assertThat(content.content().readNBytes(300_000)).isEqualTo(SupabaseResponseBodyTest.pattern(300_000, 0));
            verify(() -> { try { content.content().read(); } catch (IOException error) { throw new AssertionError(error); } }, 3);
        }
    }
    @AfterEach void stop() throws Exception {
        if (client != null) client.close();
        if (server != null) server.close();
        for (var socket : sockets) socket.close();
        if (http != null) { http.shutdownNow(); assertThat(http.awaitTermination(Duration.ofSeconds(2))).isTrue(); }
        serverThreads.shutdownNow(); assertThat(serverThreads.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
}
