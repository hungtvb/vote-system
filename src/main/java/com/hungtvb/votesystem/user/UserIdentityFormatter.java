package com.hungtvb.votesystem.user;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class UserIdentityFormatter {
    private static final int MAX_DISPLAY_NAME_CODE_POINTS = 80;

    private UserIdentityFormatter() {
    }

    public static String normalizeDisplayName(String requestedDisplayName) {
        if (requestedDisplayName == null || requestedDisplayName.isBlank()) {
            return defaultDisplayName();
        }
        String normalized = requestedDisplayName.strip().replaceAll("\\s+", " ");
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= MAX_DISPLAY_NAME_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, MAX_DISPLAY_NAME_CODE_POINTS);
        return normalized.substring(0, endIndex).stripTrailing();
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
