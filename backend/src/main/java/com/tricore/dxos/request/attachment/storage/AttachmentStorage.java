package com.tricore.dxos.request.attachment.storage;

import java.io.IOException;
import java.io.InputStream;

public interface AttachmentStorage {
    void store(String key, InputStream content) throws IOException;
    void delete(String key) throws IOException;
}
