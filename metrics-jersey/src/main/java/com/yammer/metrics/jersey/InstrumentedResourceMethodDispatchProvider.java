package com.yammer.metrics.jersey;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.spi.container.ResourceMethodDispatchProvider;
import com.sun.jersey.spi.dispatch.RequestDispatcher;
import com.yammer.metrics.annotation.ExceptionMetered;
import com.yammer.metrics.annotation.Metered;
import com.yammer.metrics.annotation.Timed;
import com.yammer.metrics.core.*;

class InstrumentedResourceMethodDispatchProvider implements ResourceMethodDispatchProvider {
    private static class TimedRequestDispatcher implements RequestDispatcher {
        private final RequestDispatcher underlying;
        private final Timer timer;

        private TimedRequestDispatcher(RequestDispatcher underlying, Timer timer) {
            this.underlying = underlying;
            this.timer = timer;
        }

        @Override
        public void dispatch(Object resource, HttpContext httpContext) {
            final TimerContext context = timer.time();
            try {
                underlying.dispatch(resource, httpContext);
            } finally {
                context.stop();
            }
        }
    }

    private static class MeteredRequestDispatcher implements RequestDispatcher {
        private final RequestDispatcher underlying;
        private final Meter meter;

        private MeteredRequestDispatcher(RequestDispatcher underlying, Meter meter) {
            this.underlying = underlying;
            this.meter = meter;
        }

        @Override
        public void dispatch(Object resource, HttpContext httpContext) {
            meter.mark();
            underlying.dispatch(resource, httpContext);
        }
    }

    private static class ExceptionMeteredRequestDispatcher implements RequestDispatcher {
        private final RequestDispatcher underlying;
        private final Meter meter;
        private final Class<? extends Throwable> exceptionClass;

        private ExceptionMeteredRequestDispatcher(RequestDispatcher underlying,
                                                  Meter meter,
                                                  Class<? extends Throwable> exceptionClass) {
            this.underlying = underlying;
            this.meter = meter;
            this.exceptionClass = exceptionClass;
        }

        @Override
        public void dispatch(Object resource, HttpContext httpContext) {
            try {
                underlying.dispatch(resource, httpContext);
            } catch (Throwable e) {
                if (exceptionClass.isAssignableFrom(e.getClass()) ||
                        (e.getCause() != null && exceptionClass.isAssignableFrom(e.getCause().getClass()))) {
                    meter.mark();
                }
                InstrumentedResourceMethodDispatchProvider.<RuntimeException>chuck(e);
            }
        }
    }

    /*
     * Leverage type erasure to throw exceptions where they shouldn't be thrown. Safer than unsafe.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void chuck(Throwable e) throws E {
        throw (E) e;
    }

    private final ResourceMethodDispatchProvider provider;
    private final MetricsRegistry registry;

    public InstrumentedResourceMethodDispatchProvider(ResourceMethodDispatchProvider provider, MetricsRegistry registry) {
        this.provider = provider;
        this.registry = registry;
    }

    @Override
    public RequestDispatcher create(AbstractResourceMethod method) {
        RequestDispatcher dispatcher = provider.create(method);
        if (dispatcher == null) {
            return null;
        }

        if (method.getMethod().isAnnotationPresent(Timed.class)) {
            final Timed annotation = method.getMethod().getAnnotation(Timed.class);
            final String name = MetricName.forTimedMethod(method.getDeclaringResource().getResourceClass(),
                                                          method.getMethod(),
                                                          annotation);
            final Timer timer = registry.add(name,
                                             new Timer(annotation.durationUnit(),
                                                       annotation.rateUnit()));
            dispatcher = new TimedRequestDispatcher(dispatcher, timer);
        }

        if (method.getMethod().isAnnotationPresent(Metered.class)) {
            final Metered annotation = method.getMethod().getAnnotation(Metered.class);
            final String name = MetricName.forMeteredMethod(method.getDeclaringResource()
                                                                  .getResourceClass(),
                                                            method.getMethod(),
                                                            annotation);
            final Meter meter = registry.add(name,
                                             new Meter(annotation.eventType(),
                                                       annotation.rateUnit()));
            dispatcher = new MeteredRequestDispatcher(dispatcher, meter);
        }

        if (method.getMethod().isAnnotationPresent(ExceptionMetered.class)) {
            final ExceptionMetered annotation = method.getMethod().getAnnotation(ExceptionMetered.class);
            final String name = MetricName.forExceptionMeteredMethod(method.getDeclaringResource()
                                                                           .getResourceClass(),
                                                                     method.getMethod(),
                                                                     annotation);
            final Meter meter = registry.add(name,
                                             new Meter(annotation.eventType(),
                                                       annotation.rateUnit()));
            dispatcher = new ExceptionMeteredRequestDispatcher(dispatcher, meter, annotation.cause());
        }

        return dispatcher;
    }
}
