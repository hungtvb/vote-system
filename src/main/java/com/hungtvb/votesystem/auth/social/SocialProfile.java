package com.hungtvb.votesystem.auth.social;

public record SocialProfile(
        SocialProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        String displayName
) {
    public SocialProfile {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Provider subject is required");
        }
        subject = subject.trim();
        email = email == null || email.isBlank() ? null : email.trim();
        displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
    }
}
