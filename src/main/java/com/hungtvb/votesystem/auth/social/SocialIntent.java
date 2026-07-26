package com.hungtvb.votesystem.auth.social;

import java.util.Locale;

public enum SocialIntent {
    AUTHENTICATE,
    CREATE_BALLOT,
    LINK_ACCOUNT;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static SocialIntent fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return AUTHENTICATE;
        }
        for (SocialIntent intent : values()) {
            if (intent.wireValue().equalsIgnoreCase(value.trim())) {
                return intent;
            }
        }
        throw new IllegalArgumentException("Unsupported social authentication intent");
    }
}
