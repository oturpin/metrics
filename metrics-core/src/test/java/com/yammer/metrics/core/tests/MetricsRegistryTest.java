package com.yammer.metrics.core.tests;

import com.yammer.metrics.core.*;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

public class MetricsRegistryTest {
    private final MetricsRegistry registry = new MetricsRegistry();

    @Test
    public void listenersRegisterNewMetrics() throws Exception {
        final MetricsRegistryListener listener = mock(MetricsRegistryListener.class);
        registry.addListener(listener);

        final Gauge<?> gauge = registry.add(MetricName.name(MetricsRegistryTest.class, "gauge"),
                                            mock(Gauge.class));
        final Counter counter = registry.add(MetricName.name(MetricsRegistryTest.class, "counter"),
                                             new Counter());
        final Histogram histogram = registry.add(MetricName.name(MetricsRegistryTest.class, "histogram"),
                                                 new Histogram(Histogram.SampleType.UNIFORM));
        final Meter meter = registry.add(MetricName.name(MetricsRegistryTest.class, "meter"),
                                         new Meter("things"));
        final Timer timer = registry.add(MetricName.name(MetricsRegistryTest.class, "timer"),
                                         new Timer());

        verify(listener).onMetricAdded(MetricName.name(MetricsRegistryTest.class, "gauge"),
                                       gauge);

        verify(listener).onMetricAdded(MetricName.name(MetricsRegistryTest.class, "counter"),
                                       counter);

        verify(listener).onMetricAdded(MetricName.name(MetricsRegistryTest.class, "histogram"),
                                       histogram);

        verify(listener).onMetricAdded(MetricName.name(MetricsRegistryTest.class, "meter"),
                                       meter);

        verify(listener).onMetricAdded(MetricName.name(MetricsRegistryTest.class, "timer"),
                                       timer);
    }

    @Test
    public void removedListenersDoNotReceiveEvents() throws Exception {
        final MetricsRegistryListener listener = mock(MetricsRegistryListener.class);
        registry.addListener(listener);

        final Counter counter1 = registry.add(MetricName.name(MetricsRegistryTest.class,
                                                              "counter1"),
                                              new Counter());

        registry.removeListener(listener);

        final Counter counter2 = registry.add(MetricName.name(MetricsRegistryTest.class,
                                                              "counter2"),
                                              new Counter());

        verify(listener).onMetricAdded(MetricName.name(MetricsRegistryTest.class, "counter1"),
                                       counter1);

        verify(listener, never()).onMetricAdded(MetricName.name(MetricsRegistryTest.class,
                                                                "counter2"),
                                                counter2);
    }

    @Test
    public void metricsCanBeRemoved() throws Exception {
        final MetricsRegistryListener listener = mock(MetricsRegistryListener.class);
        registry.addListener(listener);

        final String name = MetricName.name(MetricsRegistryTest.class, "counter1");

        final Counter counter1 = registry.add(MetricName.name(MetricsRegistryTest.class,
                                                              "counter1"),
                                              new Counter());
        registry.remove(MetricName.name(MetricsRegistryTest.class, "counter1"));

        final InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).onMetricAdded(name, counter1);
        inOrder.verify(listener).onMetricRemoved(name);
    }
}
