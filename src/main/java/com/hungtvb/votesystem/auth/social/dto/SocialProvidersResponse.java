package com.hungtvb.votesystem.auth.social.dto;

import java.util.List;

public record SocialProvidersResponse(List<String> providers) {
    public SocialProvidersResponse {
        providers = List.copyOf(providers);
    }
}
