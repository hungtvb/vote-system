package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.social.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SessionClientMetadataFactoryTests {
    private final SessionClientMetadataFactory factory = new SessionClientMetadataFactory();

    @Test
    void classifiesOnlyFixedCoarseLabelsWithoutPersistingRawUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1 secret-device-id");

        SessionClientMetadata metadata = factory.password(request);

        assertThat(metadata.provider()).isEqualTo(SessionProvider.PASSWORD);
        assertThat(metadata.clientLabel()).isEqualTo("Safari on iOS");
        assertThat(metadata.clientLabel()).doesNotContain("secret-device-id");
    }

    @Test
    void mapsSocialProviderAndUnknownClientsWithoutReflectingInput() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "custom-private-agent/123");

        SessionClientMetadata metadata = factory.social(SocialProvider.GITHUB, request);

        assertThat(metadata.provider()).isEqualTo(SessionProvider.GITHUB);
        assertThat(metadata.clientLabel()).isEqualTo("Browser on Other");
    }
}
