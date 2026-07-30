package com.hungtvb.votesystem.auth.social;

import java.util.UUID;

public interface UserProviderSummary {
    UUID getUserId();
    SocialProvider getProvider();
}
