package com.hungtvb.votesystem.auth.social;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SocialRedirects {
    private final SocialLoginProperties properties;

    public SocialRedirects(SocialLoginProperties properties) {
        this.properties = properties;
    }

    public String success(SocialProvider provider, SocialIntent intent) {
        return UriComponentsBuilder.fromUriString(properties.successUrl())
                .queryParam("social", intent == SocialIntent.LINK_ACCOUNT ? "linked" : "success")
                .queryParam("provider", provider.registrationId())
                .queryParam("intent", intent.wireValue())
                .build()
                .encode()
                .toUriString();
    }

    public String failure(String code, SocialIntent intent) {
        return UriComponentsBuilder.fromUriString(properties.failureUrl())
                .queryParam("social", "error")
                .queryParam("code", safeCode(code))
                .queryParam("intent", intent.wireValue())
                .build()
                .encode()
                .toUriString();
    }

    private String safeCode(String code) {
        if (code == null || !code.matches("[a-z0-9_\\-]{1,64}")) {
            return "oauth_failed";
        }
        return code;
    }
}
