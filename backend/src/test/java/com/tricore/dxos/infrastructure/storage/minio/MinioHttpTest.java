package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.tricore.dxos.infrastructure.storage.minio.MinioTestSupport.*;

/** Wire-level SDK checks against a local HTTP fixture, not proof of MinIO server semantics. */
class MinioHttpTest {
    @TempDir Path directory;
    HttpServer server;
    S3Client client;
    MinioObjectStorageAdapter adapter;
    MinioStorageProperties p;
    final ObjectKey key = new ObjectKey("../Wire Tiếng Việt/Key");
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start();
        var defaults = properties(directory);
        p = new MinioStorageProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), defaults.region(),
                defaults.accessKey(), defaults.secretKey(), defaults.bucket(), defaults.keyPrefix(), defaults.maxObjectBytes(),
                defaults.maxMetadataBytes(), defaults.maxKeyBytes(), directory, defaults.connectTimeout(), defaults.readTimeout(), defaults.requestTimeout(), true);
        client = MinioStorageConfiguration.createClient(p); adapter = new MinioObjectStorageAdapter(p, client, new ObjectMapper());
    }
    @AfterEach void close() { client.close(); server.stop(0); }
    @Test void signedPutContainsAtomicConditionAndExactBody() {
        AtomicReference<String> condition = new AtomicReference<>(), length = new AtomicReference<>(), path = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>(); AtomicInteger requests = new AtomicInteger();
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); condition.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            length.set(exchange.getRequestHeaders().getFirst("Content-Length")); path.set(exchange.getRequestURI().getPath());
            body.set(exchange.getRequestBody().readAllBytes()); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        var command = command(key, "wire", StoreMode.CREATE_ONLY); assertThat(adapter.store(command)).isEqualTo(command.metadata());
        assertThat(condition.get()).isEqualTo("*"); assertThat(length.get()).isEqualTo("4"); assertThat(body.get()).isEqualTo("wire".getBytes());
        assertThat(path.get()).isEqualTo("/" + p.bucket() + "/" + new MinioObjectKeyCodec(p.keyPrefix(), 1024).encode(key));
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void serverFailureHasNoMutationRetryOrProviderDetails() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] xml = "<Error><Code>InternalError</Code><Message>private-provider-detail</Message></Error>".getBytes();
            exchange.sendResponseHeaders(500, xml.length); exchange.getResponseBody().write(xml); exchange.close();
        });
        error(() -> adapter.store(command(key, "wire", StoreMode.OVERWRITE)), StorageErrorCode.UNAVAILABLE);
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void conditionalPreconditionFailureMapsToConflictOnTheWire() {
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] xml = "<Error><Code>PreconditionFailed</Code><Message>private-provider-detail</Message></Error>".getBytes();
            exchange.sendResponseHeaders(412, xml.length); exchange.getResponseBody().write(xml); exchange.close();
        });
        error(() -> adapter.store(command(key, "wire", StoreMode.CREATE_ONLY)), StorageErrorCode.CONFLICT);
    }
    @Test void getMetadataAndBytesComeFromOneResponse() throws Exception {
        var metadata = command(key, "wire", StoreMode.CREATE_ONLY).metadata(); AtomicInteger requests = new AtomicInteger();
        String envelope = new MinioMetadataCodec(new ObjectMapper(), 1800).encode(metadata);
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); exchange.getResponseHeaders().set("x-amz-meta-dxos-envelope-v1", envelope);
            exchange.getResponseHeaders().set("Content-Type", metadata.contentType());
            byte[] bytes = "wire".getBytes(); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        try (var object = adapter.read(key)) { assertThat(object.metadata()).isEqualTo(metadata); assertThat(object.content().readAllBytes()).isEqualTo("wire".getBytes()); }
        assertThat(requests.get()).isEqualTo(1);
    }
}
