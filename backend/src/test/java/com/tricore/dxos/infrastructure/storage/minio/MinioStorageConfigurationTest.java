package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static com.tricore.dxos.infrastructure.storage.minio.MinioTestSupport.*;

class MinioStorageConfigurationTest {
    @TempDir Path directory;
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MinioStorageConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);
    String[] minioProperties() {
        return new String[]{"dxos.object-storage.provider=minio", "dxos.object-storage.minio.endpoint=http://127.0.0.1:9000",
            "dxos.object-storage.minio.allow-insecure-http=true", "dxos.object-storage.minio.region=us-east-1",
            "dxos.object-storage.minio.access-key=" + java.util.UUID.randomUUID(),
            "dxos.object-storage.minio.secret-key=" + java.util.UUID.randomUUID(),
            "dxos.object-storage.minio.bucket=test-objects", "dxos.object-storage.minio.key-prefix=tests",
            "dxos.object-storage.minio.max-object-bytes=1024", "dxos.object-storage.minio.max-metadata-bytes=1800",
            "dxos.object-storage.minio.max-key-bytes=1024", "dxos.object-storage.minio.staging-directory=" + directory,
            "dxos.object-storage.minio.connect-timeout=PT1S", "dxos.object-storage.minio.read-timeout=PT1S",
            "dxos.object-storage.minio.request-timeout=PT2S"};
    }
    @Test void disabledDoesNotBindUnusedProvidersOrCreateFallback() {
        runner.run(c -> { assertThat(c).hasNotFailed(); assertThat(c).doesNotHaveBean(ObjectStoragePort.class); });
        runner.withPropertyValues("dxos.object-storage.provider=disabled").run(c -> assertThat(c).doesNotHaveBean(ObjectStoragePort.class));
    }
    @Test void minioRegistersExactlyOnePortAndDoesNotActivateSupabase() {
        runner.withPropertyValues(minioProperties()).run(c -> {
            assertThat(c).hasNotFailed(); assertThat(c).hasSingleBean(ObjectStoragePort.class);
            assertThat(c.getBean(ObjectStoragePort.class)).isInstanceOf(MinioObjectStorageAdapter.class);
            assertThat(c).hasSingleBean(S3Client.class); assertThat(c).doesNotHaveBean("objectStoragePort");
        });
    }
    @Test void missingMinioConfigFailsClosed() {
        runner.withPropertyValues("dxos.object-storage.provider=minio").run(c -> assertThat(c).hasFailed());
    }
    @Test void supabaseProviderDoesNotActivateMinioWhenSupabaseAdapterIsAbsent() {
        runner.withPropertyValues("dxos.object-storage.provider=supabase")
            .run(c -> {
                assertThat(c).hasNotFailed();
                assertThat(c).doesNotHaveBean(ObjectStoragePort.class);
                assertThat(c).doesNotHaveBean(S3Client.class);
            });
    }
    @Test void propertiesAreRedactedAndRejectUnsafeEndpointOrUnboundedLimits() {
        var p = properties(directory); assertThat(p.toString()).isEqualTo("MinioStorageProperties[redacted]");
        error(() -> new MinioStorageProperties(java.net.URI.create("http://127.0.0.1:9000"), p.region(), p.accessKey(), p.secretKey(),
                p.bucket(), p.keyPrefix(), p.maxObjectBytes(), p.maxMetadataBytes(), p.maxKeyBytes(), directory,
                p.connectTimeout(), p.readTimeout(), p.requestTimeout(), false), StorageErrorCode.INVALID_INPUT);
        error(() -> new MinioStorageProperties(java.net.URI.create("https://user:" + java.util.UUID.randomUUID() + "@example.invalid"), p.region(), p.accessKey(), p.secretKey(),
                p.bucket(), p.keyPrefix(), p.maxObjectBytes(), p.maxMetadataBytes(), p.maxKeyBytes(), directory,
                p.connectTimeout(), p.readTimeout(), p.requestTimeout(), false), StorageErrorCode.INVALID_INPUT);
        error(() -> new MinioStorageProperties(p.endpoint(), p.region(), p.accessKey(), p.secretKey(),
                p.bucket(), p.keyPrefix(), 6L * 1024 * 1024 * 1024, p.maxMetadataBytes(), p.maxKeyBytes(), directory,
                p.connectTimeout(), p.readTimeout(), p.requestTimeout(), true), StorageErrorCode.INVALID_INPUT);
    }
}
