/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.nexus.customizations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.jetty.InstrumentedQueuedThreadPool;
import com.yammer.metrics.reporting.ConsoleReporter;

@DisplayName("We enhance yammer (metrics 2.2) InstrumentedQueuedThreadPool to register its queue on JMX as well")
class JettyThreadPoolMonitoringAspectTest {
    @Test
    @DisplayName("Ensure jobs (queue) are registered on JMX")
    void checkJobsAreRegisteredInMetricsRegistry() throws Exception {
        final InstrumentedQueuedThreadPool pool = new InstrumentedQueuedThreadPool();
        final MetricsRegistry registry = Metrics.defaultRegistry();
        final MetricName metricName = new MetricName(QueuedThreadPool.class, "jobs", null);
        assertEquals(0, doCaptureGauge(registry, metricName));
        pool.setMaxThreads(1);
        pool.start();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                pool.execute(() -> {
                    try {
                        latch.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread()
                              .interrupt();
                    }
                });
            }
            assertEquals(1, doCaptureGauge(registry, metricName));
        } finally {
            latch.countDown();
            pool.stop();
        }
    }

    private int doCaptureGauge(final MetricsRegistry registry, final MetricName metricName) {
        final AtomicInteger called = new AtomicInteger(Integer.MIN_VALUE);
        new ConsoleReporter(registry, new PrintStream(new OutputStream() {
            @Override
            public void write(final int b) {
                // no-op
            }
        }), (name, metric) -> name.equals(metricName)) {
            @Override
            public void processGauge(final MetricName name, final Gauge<?> gauge, final PrintStream stream) {
                assertEquals(Integer.MIN_VALUE, called.get()); // only called once
                called.set(Number.class.cast(gauge.value()).intValue());
            }
        }.run();
        return called.get();
    }
}
