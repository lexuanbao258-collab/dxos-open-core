package com.tricore.dxos.core.data;

/**
 * Provider-neutral Data capability. All invalid inputs and operational failures use
 * StorageException. No database/object-storage distributed transaction is implied.
 */
public interface ObjectStoragePort {
    /**
     * Synchronously consumes content from its current position through EOF, requiring
     * the exact declared byte length (mismatch or caller-input read failure is INVALID_INPUT).
     * Never closes or retains caller input. Returns stored metadata on success.
     * CREATE_ONLY must enforce absence
     * even with concurrent writers; conflict leaves the existing object unchanged.
     * OVERWRITE replaces content and metadata; no version comparison is provided.
     * Other failures may have an uncertain provider outcome; no blanket rollback guarantee.
     */
    ObjectMetadata store(StoreObjectCommand command);

    /** Returns an owned read resource for try-with-resources; missing key is NOT_FOUND. */
    ObjectContent read(ObjectKey key);

    /** Returns metadata without opening a content resource; missing key is NOT_FOUND. */
    ObjectMetadata metadata(ObjectKey key);

    /** Removes the object and its metadata; missing key is NOT_FOUND, including repeated delete. */
    void delete(ObjectKey key);
}
