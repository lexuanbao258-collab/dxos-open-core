package com.tricore.dxos.infrastructure.storage.local;

import com.tricore.dxos.core.data.ObjectStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Default demo provider: a local folder, no object-store service or cloud credentials. */
@Configuration(proxyBeanMethods = false)
public class LocalObjectStorageConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "dxos.object-storage", name = "provider",
            havingValue = "local", matchIfMissing = true)
    ObjectStoragePort localObjectStorage(
            @Value("${dxos.object-storage.local.root:./.dxos-storage}") String root,
            @Value("${dxos.object-storage.local.max-object-bytes:10485760}") long maxObjectBytes,
            @Value("${dxos.object-storage.local.max-metadata-bytes:16384}") int maxMetadataBytes) {
        return new LocalFileSystemStorageAdapter(Path.of(root), maxObjectBytes, maxMetadataBytes);
    }
}
