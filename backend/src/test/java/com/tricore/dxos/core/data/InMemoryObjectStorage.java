package com.tricore.dxos.core.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Contract fake only. Small fixtures are buffered here; production adapters must stream. */
final class InMemoryObjectStorage implements ObjectStoragePort {
    private record Stored(ObjectMetadata metadata, byte[] bytes) {}

    private final Map<ObjectKey, Stored> objects = new HashMap<>();

    @Override
    public synchronized ObjectMetadata store(StoreObjectCommand command) {
        StorageValues.required(command);
        ObjectMetadata metadata = command.metadata();
        if (command.mode() == StoreMode.CREATE_ONLY && objects.containsKey(metadata.key())) {
            throw new StorageException(StorageErrorCode.CONFLICT);
        }
        byte[] bytes;
        try {
            bytes = command.content().readAllBytes();
        } catch (IOException error) {
            // Caller-input read failure is invalid input, never a provider cause.
            throw new StorageException(StorageErrorCode.INVALID_INPUT);
        }
        if (bytes.length != metadata.contentLength()) {
            throw new StorageException(StorageErrorCode.INVALID_INPUT);
        }
        objects.put(metadata.key(), new Stored(metadata, bytes));
        return metadata;
    }

    @Override
    public synchronized ObjectContent read(ObjectKey key) {
        Stored stored = load(key);
        return new ReadResource(stored);
    }

    @Override
    public synchronized ObjectMetadata metadata(ObjectKey key) {
        return load(key).metadata();
    }

    @Override
    public synchronized void delete(ObjectKey key) {
        load(key);
        objects.remove(key);
    }

    private Stored load(ObjectKey key) {
        Stored stored = objects.get(StorageValues.required(key));
        if (stored == null) {
            throw new StorageException(StorageErrorCode.NOT_FOUND);
        }
        return stored;
    }

    private static final class ReadResource implements ObjectContent {
        private final ObjectMetadata metadata;
        private final InputStream stream;
        private boolean closed;

        private ReadResource(Stored stored) {
            metadata = stored.metadata();
            ByteArrayInputStream bytes = new ByteArrayInputStream(stored.bytes());
            stream = new InputStream() {
                @Override
                public int read() {
                    requireOpen();
                    return bytes.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    requireOpen();
                    return bytes.read(buffer, offset, length);
                }

                @Override
                public void close() {
                    ReadResource.this.close();
                }
            };
        }

        @Override
        public ObjectMetadata metadata() {
            return metadata;
        }

        @Override
        public InputStream content() {
            requireOpen();
            return stream;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void requireOpen() {
            if (closed) {
                throw new StorageException(StorageErrorCode.INVALID_INPUT);
            }
        }
    }
}
