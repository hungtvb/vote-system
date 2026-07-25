package com.hungtvb.votesystem.user;

import java.util.Arrays;
import java.util.Locale;

public final class UserIdentityFormatter {
    private static final String DEFAULT_DISPLAY_NAME = "Voter";

    private UserIdentityFormatter() {
    }

    public static String displayName(String email) {
        if (email == null || email.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        String localPart = email.substring(0, Math.max(0, email.indexOf('@') >= 0 ? email.indexOf('@') : email.length()));
        String[] words = localPart.strip().split("[^\\p{L}\\p{N}]+");
        String displayName = Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .map(UserIdentityFormatter::capitalize)
                .reduce((left, right) -> left + " " + right)
                .orElse(DEFAULT_DISPLAY_NAME);
        return displayName.isBlank() ? DEFAULT_DISPLAY_NAME : displayName;
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

    private static String capitalize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return normalized;
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }
}
