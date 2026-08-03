package com.hungtvb.votesystem.auth.session;

import java.util.Objects;

public record SessionClientMetadata(
        SessionProvider provider,
        String clientLabel
) {
    public SessionClientMetadata {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(clientLabel, "clientLabel");
        if (clientLabel.isBlank() || clientLabel.length() > 64) {
            throw new IllegalArgumentException("Client label is invalid");
        }
    }
}
