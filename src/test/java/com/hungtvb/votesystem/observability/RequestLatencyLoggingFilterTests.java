package com.hungtvb.votesystem.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.AsyncContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLatencyLoggingFilterTests {

    @Test
    void recordsSynchronousApiByBoundedRouteTemplate() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestLatencyLoggingFilter filter = new RequestLatencyLoggingFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/posts");
            ((MockHttpServletResponse) servletResponse).setStatus(200);
        });

        Timer timer = registry.get("vote.http.request.duration")
                .tags(
                        "method", "GET",
                        "route", "/api/v1/posts",
                        "kind", "api",
                        "outcome", "2xx",
                        "async", "false"
                )
                .timer();

        assertThat(timer.count()).isEqualTo(1);
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(timer.getId().getTags()).noneMatch(tag -> tag.getValue().contains("00000000-0000"));
    }

    @Test
    void recordsSseOnlyWhenAsyncConnectionCompletes() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestLatencyLoggingFilter filter = new RequestLatencyLoggingFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/posts/00000000-0000-0000-0000-000000000001/events"
        );
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/api/v1/posts/{postId}/events"
            );
            ((MockHttpServletResponse) servletResponse).setStatus(200);
            servletRequest.startAsync();
        });

        assertThat(registry.find("vote.sse.connection.duration").timer()).isNull();

        AsyncContext asyncContext = request.getAsyncContext();
        asyncContext.complete();

        Timer routeTimer = registry.get("vote.http.request.duration")
                .tags(
                        "method", "GET",
                        "route", "/api/v1/posts/{postId}/events",
                        "kind", "stream",
                        "outcome", "2xx",
                        "async", "true"
                )
                .timer();
        Timer streamTimer = registry.get("vote.sse.connection.duration")
                .tag("outcome", "2xx")
                .timer();

        assertThat(routeTimer.count()).isEqualTo(1);
        assertThat(streamTimer.count()).isEqualTo(1);
        assertThat(routeTimer.getId().getTag("route"))
                .doesNotContain("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void acceptsOnlySafeInboundRequestIds() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestLatencyLoggingFilter filter = new RequestLatencyLoggingFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.addHeader("X-Request-ID", "unsafe request id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/posts"));

        assertThat(response.getHeader("X-Request-ID"))
                .isNotBlank()
                .doesNotContain("unsafe request id with spaces");
    }
}
