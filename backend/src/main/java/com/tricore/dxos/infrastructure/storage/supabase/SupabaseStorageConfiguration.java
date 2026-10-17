package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.ObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dxos.object-storage.provider", havingValue = "supabase")
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfiguration {
    @Bean(destroyMethod = "close")
    SupabaseRestStorageClient supabaseRestStorageClient(SupabaseStorageProperties properties, ObjectMapper mapper) {
        return new SupabaseRestStorageClient(properties, mapper);
    }
    @Bean
    ObjectStoragePort objectStoragePort(SupabaseStorageProperties properties, SupabaseRestStorageClient client, ObjectMapper mapper) {
        return new SupabaseObjectStorageAdapter(properties, client, mapper);
    }
}
