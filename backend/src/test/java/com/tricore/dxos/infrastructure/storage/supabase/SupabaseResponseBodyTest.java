package com.tricore.dxos.infrastructure.storage.supabase;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class SupabaseResponseBodyTest {
    final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    final AtomicInteger cancellations = new AtomicInteger();
    final AtomicInteger releases = new AtomicInteger();
    final AtomicInteger demand = new AtomicInteger();
    SupabaseResponseBody body;
    @BeforeEach void setup() {
        timer.setRemoveOnCancelPolicy(true);
        body = new SupabaseResponseBody(System.nanoTime(), TimeUnit.SECONDS.toNanos(5), releases::incrementAndGet);
        body.arm(timer);
        body.onSubscribe(new Flow.Subscription() {
            public void request(long n) { demand.addAndGet((int)n); }
            public void cancel() { cancellations.incrementAndGet(); }
        });
    }
    @AfterEach void stop() throws Exception {
        body.close(); timer.shutdownNow(); assertThat(timer.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }
    @Test void closeCancelsExactlyOnceAndRemovesScheduledTask() {
        body.close(); body.close();
        assertThat(cancellations.get()).isEqualTo(1);
        assertThat(releases.get()).isEqualTo(1);
        assertThat(timer.getQueue()).isEmpty();
    }
    @Test void backpressureKeepsOnlyOneChunkAndEofIsRepeatable() throws Exception {
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2})));
        assertThat(body.read()).isEqualTo(1);
        assertThat(demand.get()).isEqualTo(1);
        body.onComplete();
        assertThat(body.read()).isEqualTo(2);
        assertThat(body.read()).isEqualTo(-1);
        assertThat(body.read()).isEqualTo(-1);
        assertThat(timer.getQueue()).isEmpty();
    }
    @Test void singleLargeBufferPreservesContentWithoutAdditionalDemand() throws Exception {
        byte[] expected = pattern(256 * 1024 + 17, 0);
        body.onNext(List.of(ByteBuffer.wrap(expected).asReadOnlyBuffer()));
        body.onComplete();
        assertThat(body.readAllBytes()).isEqualTo(expected);
        assertThat(demand.get()).isEqualTo(1);
    }
    @Test void aggregateLargeBatchHonorsPositionsLimitsAndEmptyBuffers() throws Exception {
        byte[] expected = pattern(200_003, 0);
        var first = ByteBuffer.wrap(expected, 0, 70_001).slice();
        var second = ByteBuffer.allocateDirect(130_002);
        second.put(expected, 70_001, 130_002).flip();
        body.onNext(List.of(ByteBuffer.allocate(0), first, ByteBuffer.allocate(0), second));
        body.onComplete();
        assertThat(body.readAllBytes()).isEqualTo(expected);
    }
    @Test void manyLargeBatchesStreamWithOneOutstandingDeliveryAndExactIntegrity() throws Exception {
        // Generate 32 MiB incrementally, never constructing the whole download.
        int batchSize = 131_079, batches = 256;
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger outstanding = new AtomicInteger();
        AtomicInteger maximumOutstanding = new AtomicInteger();
        body.close();
        body = new SupabaseResponseBody(System.nanoTime(), TimeUnit.SECONDS.toNanos(5), releases::incrementAndGet);
        body.arm(timer);
        body.onSubscribe(new Flow.Subscription() {
            public void request(long n) {
                assertThat(n).isEqualTo(1);
                assertThat(outstanding.get()).isZero();
                int index = sent.getAndIncrement();
                if (index == batches) { body.onComplete(); return; }
                maximumOutstanding.accumulateAndGet(outstanding.incrementAndGet(), Math::max);
                body.onNext(List.of(ByteBuffer.wrap(pattern(batchSize, index * batchSize))));
            }
            public void cancel() { cancellations.incrementAndGet(); }
        });
        byte[] scratch = new byte[8191];
        int total = 0;
        for (int n; (n = body.read(scratch)) != -1;) {
            for (int i = 0; i < n; i++) {
                if (scratch[i] != valueAt(total + i)) fail("Content mismatch at " + (total + i));
            }
            total += n;
            if (total % batchSize == 0) outstanding.decrementAndGet();
        }
        assertThat(total).isEqualTo(batchSize * batches);
        assertThat(maximumOutstanding.get()).isEqualTo(1);
        assertThat(outstanding.get()).isZero();
    }
    @Test void closeDiscardsUnreadLargeBatchAndRejectsLateDelivery() throws Exception {
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        assertThat(body.read()).isZero();
        body.close(); body.close();
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        assertUnavailable();
        assertThat(cancellations.get()).isEqualTo(1);
        assertThat(releases.get()).isEqualTo(1);
        assertThat(demand.get()).isEqualTo(1);
        assertThat(timer.getQueue()).isEmpty();
    }
    @Test void absoluteDeadlineDiscardsCompletedButUnreadLargeBatch() throws Exception {
        shortDeadline();
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        body.onComplete();
        Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            while (cancellations.get() == 0) Thread.sleep(5);
        });
        assertUnavailable();
        assertThat(demand.get()).isEqualTo(1);
    }
    @Test void providerStallAfterLargePartialDownloadWakesBlockedReader() throws Exception {
        shortDeadline();
        byte[] partial = pattern(300_000, 0);
        body.onNext(List.of(ByteBuffer.wrap(partial)));
        assertThat(body.readNBytes(partial.length)).isEqualTo(partial);
        assertUnavailable();
        assertThat(demand.get()).isEqualTo(2);
        assertThat(cancellations.get()).isEqualTo(1);
    }
    @Test void closeWakesBlockedReaderAfterLargeBatch() throws Exception {
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        assertThat(body.readNBytes(300_000)).hasSize(300_000);
        try (var reader = Executors.newSingleThreadExecutor()) {
            var waiting = reader.submit(() -> { assertUnavailable(); });
            body.close();
            waiting.get(1, TimeUnit.SECONDS);
        }
    }
    @Test void providerErrorDiscardsLargeBatchWithSanitizedFailure() {
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        body.onError(new IllegalStateException("private provider details"));
        assertUnavailable();
        assertThat(cancellations.get()).isEqualTo(1);
    }
    @Test void unsolicitedSecondBatchIsCancelledRatherThanQueued() {
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        body.onNext(List.of(ByteBuffer.allocate(300_000)));
        assertUnavailable();
        assertThat(demand.get()).isEqualTo(1);
        assertThat(cancellations.get()).isEqualTo(1);
    }
    @Test void emptyDeliveriesDoNotRecurseOrLoseDemand() throws Exception {
        body.close();
        body = new SupabaseResponseBody(System.nanoTime(), TimeUnit.SECONDS.toNanos(5), releases::incrementAndGet);
        body.arm(timer);
        AtomicInteger sent = new AtomicInteger();
        body.onSubscribe(new Flow.Subscription() {
            public void request(long n) {
                if (sent.incrementAndGet() <= 10_000) body.onNext(List.of(ByteBuffer.allocate(0)));
                else body.onComplete();
            }
            public void cancel() { cancellations.incrementAndGet(); }
        });
        assertThat(body.read()).isEqualTo(-1);
        assertThat(sent.get()).isEqualTo(10_001);
    }
    void shortDeadline() {
        body.close(); cancellations.set(0); releases.set(0); demand.set(0);
        body = new SupabaseResponseBody(System.nanoTime(), TimeUnit.MILLISECONDS.toNanos(500), releases::incrementAndGet);
        body.arm(timer);
        body.onSubscribe(new Flow.Subscription() {
            public void request(long n) { demand.addAndGet((int)n); }
            public void cancel() { cancellations.incrementAndGet(); }
        });
    }
    void assertUnavailable() {
        assertThatThrownBy(body::read).isInstanceOf(IOException.class).hasMessage("Response unavailable")
                .hasNoCause().hasNoSuppressedExceptions();
    }
    static byte[] pattern(int size, int offset) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = valueAt(offset + i);
        return bytes;
    }
    static byte valueAt(int offset) { return (byte) (offset * 31 + offset / 251); }
}
