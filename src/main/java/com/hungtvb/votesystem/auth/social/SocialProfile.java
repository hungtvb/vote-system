package com.hungtvb.votesystem.auth.social;

public record SocialProfile(
        SocialProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        String displayName
) {
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 320;

    public SocialProfile {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Provider subject is required");
        }
        subject = subject.trim();
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Provider subject is too long");
        }

        email = email == null || email.isBlank() ? null : email.trim();
        if (email != null && email.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Provider email is too long");
        }
        displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
    }
}
