package com.tricore.dxos.infrastructure.storage.supabase;

import com.tricore.dxos.core.data.*;
import java.io.*;

/** One provider response. No provider checked exception or cause crosses this resource. */
final class SupabaseObjectContent extends InputStream implements ObjectContent {
    private final ObjectMetadata metadata;
    private final InputStream provider;
    private long consumed;
    private boolean closed;
    SupabaseObjectContent(ObjectMetadata metadata, InputStream provider) {
        this.metadata = metadata; this.provider = provider;
    }
    @Override public ObjectMetadata metadata() { return metadata; }
    @Override public InputStream content() { requireOpen(); return this; }
    @Override public int read() {
        byte[] one = new byte[1]; int n = read(one, 0, 1); return n == -1 ? -1 : one[0] & 255;
    }
    @Override public int read(byte[] b, int off, int len) {
        requireOpen();
        if (b == null || off < 0 || len < 0 || off > b.length - len) throw invalid();
        if (len == 0) return 0;
        try {
            int n = provider.read(b, off, len);
            if (n == 0) { int one = provider.read(); if (one == -1) n = -1; else { b[off] = (byte) one; n = 1; } }
            if (n < -1 || n > len || (n == -1 && consumed != metadata.contentLength())
                    || (n > 0 && n > metadata.contentLength() - consumed)) throw unavailable();
            if (n > 0) consumed += n;
            return n;
        } catch (IOException | RuntimeException error) { failAndRelease(); throw unavailable(); }
    }
    @Override public long skip(long n) {
        requireOpen(); if (n < 0) throw invalid();
        byte[] buffer = new byte[(int)Math.min(8192, n)]; long skipped = 0;
        while (skipped < n) { int r = read(buffer, 0, (int)Math.min(buffer.length, n-skipped)); if (r == -1) break; skipped += r; }
        return skipped;
    }
    @Override public int available() {
        requireOpen(); try { return provider.available(); }
        catch (IOException | RuntimeException error) { failAndRelease(); throw unavailable(); }
    }
    @Override public byte[] readNBytes(int len) {
        requireOpen(); if (len < 0) throw invalid();
        try { return super.readNBytes(len); } catch (IOException error) { throw unavailable(); }
    }
    @Override public int readNBytes(byte[] b, int off, int len) {
        requireOpen(); if (b == null || off < 0 || len < 0 || off > b.length-len) throw invalid();
        try { return super.readNBytes(b, off, len); } catch (IOException error) { throw unavailable(); }
    }
    @Override public void skipNBytes(long n) {
        requireOpen(); if (n < 0) throw invalid();
        long skipped = skip(n);
        if (skipped != n) throw unavailable();
    }
    @Override public boolean markSupported() { return false; }
    @Override public synchronized void mark(int readlimit) { requireOpen(); throw invalid(); }
    @Override public synchronized void reset() { requireOpen(); throw invalid(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        try { provider.close(); } catch (IOException | RuntimeException error) { throw unavailable(); }
    }
    private void failAndRelease() {
        try { close(); } catch (StorageException ignored) { /* retain sanitized read failure */ }
    }
    private void requireOpen() { if (closed) throw invalid(); }
    private static StorageException invalid() { return new StorageException(StorageErrorCode.INVALID_INPUT); }
    private static StorageException unavailable() { return new StorageException(StorageErrorCode.UNAVAILABLE); }
}
