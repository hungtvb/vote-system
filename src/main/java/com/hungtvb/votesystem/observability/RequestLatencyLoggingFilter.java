package com.hungtvb.votesystem.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLatencyLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLatencyLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String START_NANOS_ATTRIBUTE = RequestLatencyLoggingFilter.class.getName() + ".startNanos";
    public static final String REQUEST_ID_ATTRIBUTE = RequestLatencyLoggingFilter.class.getName() + ".requestId";
    private static final String COMPLETED_ATTRIBUTE = RequestLatencyLoggingFilter.class.getName() + ".completed";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final double[] PERCENTILES = {0.5, 0.9, 0.95, 0.99};

    private final MeterRegistry meterRegistry;

    public RequestLatencyLoggingFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, resolveRequestId(request));
        request.setAttribute(COMPLETED_ATTRIBUTE, new AtomicBoolean());
        response.setHeader(REQUEST_ID_HEADER, requestId(request));

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                registerAsyncCompletion(request, response);
            } else {
                recordCompletion(request, response, outcome(response.getStatus()));
            }
        }
    }

    private void registerAsyncCompletion(HttpServletRequest request, HttpServletResponse response) {
        AsyncListener listener = new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                recordCompletion(request, response, outcome(response.getStatus()));
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                recordCompletion(request, response, "timeout");
            }

            @Override
            public void onError(AsyncEvent event) {
                recordCompletion(request, response, "error");
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                event.getAsyncContext().addListener(this);
            }
        };

        try {
            request.getAsyncContext().addListener(listener, request, response);
        } catch (IllegalStateException completedBeforeListenerRegistration) {
            recordCompletion(request, response, outcome(response.getStatus()));
        }
    }

    private void recordCompletion(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String completionOutcome) {
        AtomicBoolean completed = (AtomicBoolean) request.getAttribute(COMPLETED_ATTRIBUTE);
        if (completed == null || !completed.compareAndSet(false, true)) {
            return;
        }

        long durationNanos = System.nanoTime() - startNanos(request);
        String route = routeTemplate(request);
        String kind = requestKind(route, request.getRequestURI());
        String method = request.getMethod();
        boolean async = kind.equals("stream");

        Timer.builder("vote.http.request.duration")
                .description("Completed HTTP request duration split by bounded route template and request kind")
                .tags(
                        "method", method,
                        "route", route,
                        "kind", kind,
                        "outcome", completionOutcome,
                        "async", Boolean.toString(async)
                )
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        if (kind.equals("stream")) {
            Timer.builder("vote.sse.connection.duration")
                    .description("Completed SSE connection duration")
                    .tags("outcome", completionOutcome)
                    .publishPercentiles(PERCENTILES)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }

        log.info(
                "http_request_complete requestId={} method={} route={} status={} durationMs={} async={} kind={} outcome={}",
                requestId(request),
                method,
                route,
                response.getStatus(),
                TimeUnit.NANOSECONDS.toMillis(durationNanos),
                async,
                kind,
                completionOutcome
        );
    }

    private String resolveRequestId(HttpServletRequest request) {
        String candidate = request.getHeader(REQUEST_ID_HEADER);
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value == null ? "missing" : value.toString();
    }

    private long startNanos(HttpServletRequest request) {
        Object value = request.getAttribute(START_NANOS_ATTRIBUTE);
        return value instanceof Long start ? start : System.nanoTime();
    }

    private String routeTemplate(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (value != null) {
            return value.toString();
        }
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            return "/actuator/**";
        }
        return "UNMATCHED";
    }

    private String requestKind(String route, String requestUri) {
        if (route.endsWith("/events") || requestUri.endsWith("/events")) {
            return "stream";
        }
        if (route.startsWith("/actuator") || requestUri.startsWith("/actuator")) {
            return "actuator";
        }
        return "api";
    }

    private String outcome(int status) {
        if (status >= 100 && status < 600) {
            return (status / 100) + "xx";
        }
        return "unknown";
    }
}
