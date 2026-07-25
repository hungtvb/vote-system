package com.hungtvb.votesystem.user;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class UserIdentityFormatter {
    private UserIdentityFormatter() {
    }

    public static String normalizeDisplayName(String requestedDisplayName) {
        if (requestedDisplayName == null || requestedDisplayName.isBlank()) {
            return defaultDisplayName();
        }
        return requestedDisplayName.strip().replaceAll("\\s+", " ");
    }

    public static String defaultDisplayName() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "Voter " + suffix;
    }

    public static String initials(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "V";
        }
        String initials = Arrays.stream(displayName.strip().split("\\s+"))
                .filter(word -> !word.isBlank())
                .limit(2)
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT))
                .reduce("", String::concat);
        return initials.isBlank() ? "V" : initials;
    }
}
