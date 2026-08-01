package com.hungtvb.votesystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.common.config.CorsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CookieOriginValidationFilterTests {
    private final CookieOriginValidationFilter filter = new CookieOriginValidationFilter(
            new CorsProperties(List.of("https://app.ballotbox.io.vn")),
            new ObjectMapper()
    );

    @Test
    void rejectsCrossSiteOriginForRefresh() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/refresh");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SESSION_ORIGIN_REJECTED");
        verifyNoInteractions(chain);
    }

    @Test
    void forgedForwardedHeadersCannotTurnAttackerOriginIntoAllowedOrigin() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/refresh");
        request.addHeader("Origin", "https://attacker.example");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void allowsExplicitConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/refresh");
        request.addHeader("Origin", "https://app.ballotbox.io.vn");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void rejectsApiOriginUnlessItIsExplicitlyAllowlisted() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/logout");
        request.addHeader("Origin", "https://api.ballotbox.io.vn");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsFetchMetadataMarkedCrossSiteWithoutOrigin() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/logout");
        request.addHeader("Sec-Fetch-Site", "cross-site");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsSameSiteBrowserPostWithoutOrigin() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/logout");
        request.addHeader("Sec-Fetch-Site", "same-site");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void preservesNonBrowserApiClientsWithoutFetchMetadata() throws Exception {
        MockHttpServletRequest request = post("/api/v1/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setScheme("https");
        request.setServerName("api.ballotbox.io.vn");
        request.setServerPort(443);
        return request;
    }
}
