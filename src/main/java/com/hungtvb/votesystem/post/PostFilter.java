package com.hungtvb.votesystem.post;

import java.util.Locale;
import java.util.UUID;

public record PostFilter(
        String query,
        String category,
        BallotStatus status,
        UUID authorId
) {
    public PostFilter {
        query = normalize(query);
        category = normalizeCategory(category);
    }

    public static PostFilter empty() {
        return new PostFilter(null, null, null, null);
    }

    public PostFilter withAuthorId(UUID nextAuthorId) {
        return new PostFilter(query, category, status, nextAuthorId);
    }

    public boolean isEmpty() {
        return query == null && category == null && status == null && authorId == null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String normalizeCategory(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
