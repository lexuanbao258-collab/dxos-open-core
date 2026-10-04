package com.tricore.dxos.request.attachment.storage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "dxos.attachments")
public record AttachmentProperties(@NotNull Path storagePath, @NotNull DataSize maxFileSize) {
    @AssertTrue(message = "Attachment maximum file size must be positive")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }
}
