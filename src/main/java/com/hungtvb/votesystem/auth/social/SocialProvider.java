package com.hungtvb.votesystem.auth.social;

import com.hungtvb.votesystem.common.error.ResourceNotFoundException;

import java.util.Locale;

public enum SocialProvider {
    GOOGLE,
    GITHUB;

    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SocialProvider fromRegistrationId(String registrationId) {
        for (SocialProvider provider : values()) {
            if (provider.registrationId().equalsIgnoreCase(registrationId)) {
                return provider;
            }
        }
        throw new ResourceNotFoundException("Social login provider is not supported");
    }
}
