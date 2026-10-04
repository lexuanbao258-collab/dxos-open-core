package com.tricore.dxos.request.attachment.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentConfiguration {
    @Bean
    public AttachmentStorage attachmentStorage(AttachmentProperties properties) {
        return new LocalFilesystemAttachmentStorage(properties.storagePath());
    }
}
