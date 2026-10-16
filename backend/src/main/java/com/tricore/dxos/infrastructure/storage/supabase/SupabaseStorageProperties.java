package com.tricore.dxos.infrastructure.storage.supabase;

import com.tricore.dxos.core.data.StorageErrorCode;
import com.tricore.dxos.core.data.StorageException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** Required only when dxos.object-storage.provider=supabase. Never prints credentials. */
@ConfigurationProperties("dxos.object-storage.supabase")
public record SupabaseStorageProperties(URI restBaseUrl, String apiKey, String apiKeyMode,
        String bucket, String keyPrefix, Long maxObjectBytes, Integer maxMetadataBytes,
        Integer maxKeyBytes, Integer maxResponseBytes, Path stagingDirectory,
        Duration connectTimeout, Duration requestTimeout) {
    public SupabaseStorageProperties {
        boolean valid = restBaseUrl != null && "https".equals(restBaseUrl.getScheme())
                && restBaseUrl.getHost() != null && restBaseUrl.getUserInfo() == null
                && restBaseUrl.getQuery() == null && restBaseUrl.getFragment() == null
                && restBaseUrl.getPath().matches("/storage/v1/?")
                && apiKey != null && !apiKey.isBlank() && apiKey.chars().allMatch(c -> c >= 33 && c <= 126)
                && ("secret".equals(apiKeyMode) || "service-role".equals(apiKeyMode))
                && bucket != null && bucket.matches("[a-z0-9][a-z0-9-]*")
                && keyPrefix != null && keyPrefix.matches("[A-Za-z0-9_-]+")
                && maxObjectBytes != null && maxObjectBytes >= 0
                && maxMetadataBytes != null && maxMetadataBytes > 0
                && maxKeyBytes != null && maxKeyBytes > 0
                && maxResponseBytes != null && maxResponseBytes > maxMetadataBytes && maxResponseBytes < Integer.MAX_VALUE
                && stagingDirectory != null && stagingDirectory.isAbsolute()
                && positive(connectTimeout) && positive(requestTimeout);
        if (!valid) throw new StorageException(StorageErrorCode.INVALID_INPUT);
    }
    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
    @Override public String toString() { return "SupabaseStorageProperties[redacted]"; }
}
