package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.*;
import com.tricore.dxos.core.data.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.UUID;

/** REST-only, version-addressed reads; no automatic application write retries. */
final class SupabaseRestStorageClient implements AutoCloseable {
    interface Transport { HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException; }
    record Info(String version, long size, JsonNode metadata, String etag) {}
    private final SupabaseStorageProperties properties;
    private final ObjectMapper mapper;
    private final Transport transport;
    private final HttpClient ownedClient;
    SupabaseRestStorageClient(SupabaseStorageProperties properties, ObjectMapper mapper) {
        this.properties = properties; this.mapper = mapper;
        ownedClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        transport = request -> ownedClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }
    SupabaseRestStorageClient(SupabaseStorageProperties properties, ObjectMapper mapper, Transport transport) {
        this.properties = properties; this.mapper = mapper; this.transport = transport; ownedClient = null;
    }
    Info info(String key, String version) {
        JsonNode json = json(send(request("object/info/authenticated/" + objectPath(key), version).GET().build(), false), false);
        try {
            String actual = json.path("version").textValue();
            if (!UUID.fromString(actual).toString().equals(actual)) throw unavailable();
            if (version != null && !version.equals(actual)) throw unavailable();
            if (!json.path("name").asText().equals(key) || !json.path("bucket_id").asText().equals(properties.bucket())
                    || !json.path("size").isIntegralNumber() || !json.path("size").canConvertToLong()
                    || json.path("size").longValue() < 0 || json.path("is_delete_marker").asBoolean(false)) throw unavailable();
            return new Info(actual, json.path("size").longValue(), json.path("metadata"), json.path("etag").asText(""));
        } catch (RuntimeException error) { throw unavailable(); }
    }
    InputStream download(String key, Info info) {
        HttpResponse<InputStream> response = null;
        try {
        var builder = request("object/authenticated/" + objectPath(key), info.version()).GET();
        if (!info.etag().isEmpty()) builder.header("If-Match", info.etag());
        response = send(builder.build(), false);
            if (response.statusCode() != 200) throw unavailable();
            var length = response.headers().firstValue("content-length");
            if (length.isPresent() && Long.parseLong(length.get()) != info.size()) throw unavailable();
            if (!info.etag().isEmpty() && !response.headers().firstValue("etag").orElse("").equals(info.etag())) throw unavailable();
            return response.body();
        } catch (RuntimeException error) {
            if (response != null) release(response.body());
            if (error instanceof StorageException normalized) throw normalized;
            throw unavailable();
        }
    }
    void upload(String key, Path file, String contentType, String metadata, StoreMode mode) {
        try (InputStream staged = java.nio.file.Files.newInputStream(file)) {
            long length = java.nio.file.Files.size(file);
            HttpRequest.BodyPublisher body = length == 0 ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> staged), length);
            var req = request("object/" + objectPath(key), null)
                    .header("Content-Type", contentType).header("x-metadata", metadata)
                    .header("x-upsert", Boolean.toString(mode == StoreMode.OVERWRITE))
                    .header("Cache-Control", "no-store")
                    .POST(body).build();
            json(send(req, mode == StoreMode.CREATE_ONLY), mode == StoreMode.CREATE_ONLY);
        } catch (StorageException error) { throw error; }
        catch (Exception error) { throw SupabaseStorageErrorMapper.transport(error); }
    }
    void delete(String key) {
        json(send(request("object/" + objectPath(key), null).DELETE().build(), false), false);
    }
    private String objectPath(String key) { return properties.bucket() + "/" + key; }
    private HttpRequest.Builder request(String relative, String version) {
        try {
            String base = properties.restBaseUrl().toString();
            URI uri = URI.create(base + (base.endsWith("/") ? "" : "/") + relative
                    + (version == null ? "" : "?versionId=" + UUID.fromString(version)));
            var request = HttpRequest.newBuilder(uri).timeout(properties.requestTimeout())
                    .header("apikey", properties.apiKey()).header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache, no-store");
            if ("service-role".equals(properties.apiKeyMode())) request.header("Authorization", "Bearer " + properties.apiKey());
            return request;
        } catch (RuntimeException error) { throw unavailable(); }
    }
    private HttpResponse<InputStream> send(HttpRequest request, boolean createOnly) {
        try {
            HttpResponse<InputStream> response = transport.send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                JsonNode body = null;
                try { body = json(response, createOnly); } catch (StorageException ignored) { /* malformed errors still sanitized */ }
                throw SupabaseStorageErrorMapper.response(response.statusCode(), body, createOnly);
            }
            return response;
        } catch (StorageException error) { throw error; }
        catch (Exception error) { throw SupabaseStorageErrorMapper.transport(error); }
    }
    private JsonNode json(HttpResponse<InputStream> response, boolean createOnly) {
        try (InputStream body = response.body()) {
            int max = properties.maxResponseBytes();
            byte[] bytes = body.readNBytes(max + 1);
            if (bytes.length > max) throw unavailable();
            if (bytes.length == 0) return mapper.createObjectNode();
            JsonNode node = mapper.readTree(bytes);
            if (node == null || !node.isObject()) throw unavailable();
            return node;
        } catch (Exception error) { throw unavailable(); }
    }
    private static void release(InputStream body) { try { body.close(); } catch (Exception ignored) { /* normalized by caller */ } }
    private static StorageException unavailable() { return new StorageException(StorageErrorCode.UNAVAILABLE); }
    @Override public void close() { if (ownedClient != null) ownedClient.close(); }
}
