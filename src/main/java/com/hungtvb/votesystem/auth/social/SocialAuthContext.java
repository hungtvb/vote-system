package com.hungtvb.votesystem.auth.social;

import java.io.Serializable;
import java.util.UUID;

public record SocialAuthContext(SocialIntent intent, UUID linkUserId) implements Serializable {
    public static final String SESSION_ATTRIBUTE = SocialAuthContext.class.getName();

    public static SocialAuthContext authenticate(SocialIntent intent) {
        if (intent == SocialIntent.LINK_ACCOUNT) {
            throw new IllegalArgumentException("Link intent requires an authenticated user");
        }
        return new SocialAuthContext(intent, null);
    }

    public static SocialAuthContext link(UUID userId) {
        return new SocialAuthContext(SocialIntent.LINK_ACCOUNT, userId);
    }
}
