package com.tricore.dxos.infrastructure.storage.minio;

import com.tricore.dxos.core.data.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** Explicit provider credentials; never include property values in diagnostics. */
@ConfigurationProperties("dxos.object-storage.minio")
public record MinioStorageProperties(URI endpoint, String region, String accessKey, String secretKey,
        String bucket, String keyPrefix, Long maxObjectBytes, Integer maxMetadataBytes,
        Integer maxKeyBytes, Path stagingDirectory, Duration connectTimeout, Duration readTimeout,
        Duration requestTimeout, boolean allowInsecureHttp) {
    public MinioStorageProperties {
        boolean valid = endpoint != null && endpoint.getHost() != null && endpoint.getUserInfo() == null
                && endpoint.getQuery() == null && endpoint.getFragment() == null
                && (endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                && ("https".equals(endpoint.getScheme()) || (allowInsecureHttp && "http".equals(endpoint.getScheme())))
                && region != null && region.matches("[a-z0-9][a-z0-9-]{0,62}")
                && secret(accessKey) && accessKey.length() >= 3 && secret(secretKey) && secretKey.length() >= 8
                && bucket != null && bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                && keyPrefix != null && keyPrefix.matches("[A-Za-z0-9_-]+") && keyPrefix.length() <= 64
                && maxObjectBytes != null && maxObjectBytes >= 0 && maxObjectBytes <= 5L * 1024 * 1024 * 1024
                && maxMetadataBytes != null && maxMetadataBytes > 0 && maxMetadataBytes <= 1900
                && maxKeyBytes != null && maxKeyBytes > 0 && maxKeyBytes <= 1024 && maxKeyBytes > keyPrefix.length() + 4
                && stagingDirectory != null && stagingDirectory.isAbsolute()
                && positive(connectTimeout) && positive(readTimeout) && positive(requestTimeout);
        if (!valid) throw new StorageException(StorageErrorCode.INVALID_INPUT);
    }
    private static boolean text(String s) { return s != null && !s.isBlank(); }
    private static boolean secret(String s) { return text(s) && s.chars().allMatch(c -> c >= 33 && c <= 126); }
    private static boolean positive(Duration d) { return d != null && d.compareTo(Duration.ofMillis(1)) >= 0
                && d.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0; }
    @Override public String toString() { return "MinioStorageProperties[redacted]"; }
}
