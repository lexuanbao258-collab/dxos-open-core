package com.tricore.dxos.infrastructure.storage.supabase;

import java.io.*;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** One outstanding bounded chunk; cancellation never waits for a blocked reader. */
final class SupabaseResponseBody extends InputStream implements HttpResponse.BodySubscriber<InputStream> {
    private static final int MAX_CHUNK_BYTES = 64 * 1024;
    private final CompletableFuture<InputStream> body = CompletableFuture.completedFuture(this);
    private final long started;
    private final long budget;
    private final Runnable released;
    private ScheduledFuture<?> timeout;
    private Flow.Subscription subscription;
    private byte[] pending;
    private int position;
    private boolean complete, closed, failed, exhausted, resourcesReleased;

    SupabaseResponseBody(long started, long budget, Runnable released) {
        this.started = started; this.budget = budget; this.released = released;
    }
    synchronized void arm(ScheduledExecutorService timer) {
        timeout = timer.schedule(this::expire, Math.max(0, remaining()), TimeUnit.NANOSECONDS);
    }
    private long remaining() { return budget - (System.nanoTime() - started); }
    @Override public CompletionStage<InputStream> getBody() { return body; }
    @Override public synchronized void onSubscribe(Flow.Subscription value) {
        if (subscription != null || closed || failed) { value.cancel(); return; }
        subscription = value;
        value.request(1);
    }
    @Override public synchronized void onNext(List<ByteBuffer> buffers) {
        if (closed || failed || exhausted) return;
        long size = 0;
        for (var buffer : buffers) size += buffer.remaining();
        if (pending != null || size > MAX_CHUNK_BYTES || remaining() <= 0) { expire(); return; }
        pending = new byte[(int) size]; position = 0;
        int offset = 0;
        for (var buffer : buffers) { int n = buffer.remaining(); buffer.get(pending, offset, n); offset += n; }
        notifyAll();
    }
    @Override public synchronized void onError(Throwable ignored) { expire(); }
    @Override public synchronized void onComplete() {
        complete = true;
        // Keep the deadline until the caller consumes/closes even an already buffered lazy body.
        notifyAll();
    }
    private synchronized void expire() {
        if (closed || failed || exhausted) return;
        failed = true; pending = null;
        cancel(); notifyAll();
    }
    private void cancel() {
        if (resourcesReleased) return;
        resourcesReleased = true;
        if (timeout != null) timeout.cancel(false);
        if (subscription != null) subscription.cancel();
        released.run();
    }
    @Override public int read() throws IOException {
        byte[] one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
    }
    @Override public synchronized int read(byte[] bytes, int off, int len) throws IOException {
        java.util.Objects.checkFromIndexSize(off, len, bytes.length);
        if (len == 0) return 0;
        for (;;) {
            if (!closed && !exhausted && remaining() <= 0) expire();
            if (closed || failed) throw new IOException("Response unavailable");
            if (pending != null && position < pending.length) {
                int n = Math.min(len, pending.length - position);
                System.arraycopy(pending, position, bytes, off, n); position += n;
                return n;
            }
            if (pending != null) { pending = null; if (!complete) subscription.request(1); continue; }
            if (complete) { exhausted = true; cancel(); return -1; }
            try { TimeUnit.NANOSECONDS.timedWait(this, Math.max(1, remaining())); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); expire(); throw new IOException("Response unavailable"); }
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; pending = null; cancel(); notifyAll();
    }
}
