package com.tricore.dxos.infrastructure.storage.supabase;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Local-only regressions; never launch a worker or access a hosted Supabase project. */
class SupabaseHostedWorkerCleanupTest {

    @Test void absentWorkerNeedsNoCleanup() {
        assertThat(SupabaseObjectStorageIntegrationTest.terminateWorker(null)).isTrue();
    }

    @Test void normalZeroExitIsConfirmedSafe() throws Exception {
        Process worker = mock(Process.class);
        when(worker.isAlive()).thenReturn(false);
        when(worker.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
        when(worker.exitValue()).thenReturn(0);

        assertThat(SupabaseObjectStorageIntegrationTest.terminateWorker(worker)).isTrue();
        verify(worker, never()).destroyForcibly();
    }

    @Test void forciblyStoppedWorkerRequiresReconciliationEvenAfterReaping() throws Exception {
        Process worker = mock(Process.class);
        when(worker.isAlive()).thenReturn(true);
        when(worker.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);

        assertThat(SupabaseObjectStorageIntegrationTest.terminateWorker(worker)).isFalse();
        verify(worker).destroyForcibly();
    }

    @Test void failedWorkerRequiresReconciliationEvenAfterReaping() throws Exception {
        Process worker = mock(Process.class);
        when(worker.isAlive()).thenReturn(false);
        when(worker.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
        when(worker.exitValue()).thenReturn(1);

        assertThat(SupabaseObjectStorageIntegrationTest.terminateWorker(worker)).isFalse();
        verify(worker, never()).destroyForcibly();
    }

    @Test void workerThatDoesNotExitRequiresReconciliation() throws Exception {
        Process worker = mock(Process.class);
        when(worker.isAlive()).thenReturn(true);
        when(worker.waitFor(10, TimeUnit.SECONDS)).thenReturn(false);

        assertThat(SupabaseObjectStorageIntegrationTest.terminateWorker(worker)).isFalse();
        verify(worker).destroyForcibly();
    }

    @Test void interruptionPreservesInterruptStatusAndRequiresReconciliation() throws Exception {
        Process worker = mock(Process.class);
        when(worker.isAlive()).thenReturn(false);
        when(worker.waitFor(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThat(SupabaseObjectStorageIntegrationTest.terminateWorker(worker)).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // Do not poison the JUnit test runner thread.
        }
    }
}
