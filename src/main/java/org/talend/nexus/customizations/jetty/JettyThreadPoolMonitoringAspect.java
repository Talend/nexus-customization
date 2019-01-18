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
package org.talend.nexus.customizations.jetty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.jetty.InstrumentedQueuedThreadPool;

@Aspect
public class JettyThreadPoolMonitoringAspect {
    @AfterReturning(value = "execution(com.yammer.metrics.jetty.InstrumentedQueuedThreadPool.new(com.yammer.metrics.core.MetricsRegistry)) && this(pool) && args(registry)", argNames = "pool,registry")
    public void registerJobsGauge(final InstrumentedQueuedThreadPool pool, final MetricsRegistry registry) {
        final MetricName jobsMetricName = new MetricName(QueuedThreadPool.class, "jobs", null);
        if (registry.allMetrics().containsKey(jobsMetricName)) {
            getLogger().warn("[TALEND CUSTOMIZATION] {} already registered, skipping", jobsMetricName);
            return; // nexus included it, skip our logic
        }
        try {
            final Method getQueue = QueuedThreadPool.class.getDeclaredMethod("getQueue");
            if (!getQueue.isAccessible()) {
                getQueue.setAccessible(true);
            }
            registry.newGauge(QueuedThreadPool.class, "jobs", new Gauge<Integer>() {
                @Override
                public Integer value() {
                    final BlockingQueue<Runnable> queue;
                    try {
                        queue = BlockingQueue.class.cast(getQueue.invoke(pool));
                    } catch (final IllegalAccessException e) {
                        getLogger().error("[TALEND CUSTOMIZATION] " + e.getMessage(), e);
                        return -10;
                    } catch (final InvocationTargetException e) {
                        getLogger().error("[TALEND CUSTOMIZATION] " + e.getMessage(), e.getTargetException());
                        return -10;
                    }
                    return queue == null ? 0 : queue.size();
                }
            });
            getLogger().info("[TALEND CUSTOMIZATION] Registered metrics {}", jobsMetricName);
        } catch (final NoSuchMethodException e) {
            getLogger().error("[TALEND CUSTOMIZATION] Can't register {} metrics", jobsMetricName, e);
        }
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(JettyThreadPoolMonitoringAspect.class);
    }
}
