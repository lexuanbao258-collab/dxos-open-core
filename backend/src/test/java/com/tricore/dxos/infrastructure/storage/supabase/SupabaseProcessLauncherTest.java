package com.tricore.dxos.infrastructure.storage.supabase;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class SupabaseProcessLauncherTest {
    @Test void launchesWorkerWithCurrentJdkAndClasspathWithoutProviderConfiguration() throws Exception {
        var executable = SupabaseProcessWorker.javaExecutable();
        assertThat(executable).isRegularFile();
        var process = new ProcessBuilder(executable.toString(), "-cp",
                System.getProperty("workflow.test.runtimeClasspath"), SupabaseProcessWorker.class.getName(),
                "launcher-probe").redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(new String(process.getInputStream().readAllBytes()).strip())
                    .isEqualTo("JDK=" + System.getProperty("java.home"));
        } finally {
            process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
