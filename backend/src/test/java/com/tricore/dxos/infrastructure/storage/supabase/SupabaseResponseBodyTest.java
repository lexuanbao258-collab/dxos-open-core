package com.tricore.dxos.infrastructure.storage.supabase;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

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
    @Test void oversizedTransportChunkFailsWithoutCopyingOrRetainingIt() {
        body.onNext(List.of(ByteBuffer.allocate(65537)));
        assertThatThrownBy(body::read).isInstanceOf(IOException.class).hasMessage("Response unavailable").hasNoCause();
        assertThat(cancellations.get()).isEqualTo(1);
        assertThat(timer.getQueue()).isEmpty();
    }
}
