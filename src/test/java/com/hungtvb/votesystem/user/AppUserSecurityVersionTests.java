package com.hungtvb.votesystem.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserSecurityVersionTests {

    @Test
    void restrictionRotatesTokensButExpiryNormalizationDoesNotRotateAgain() {
        AppUser user = AppUser.create("user@example.com", "hash");
        Instant restrictedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant expiresAt = restrictedAt.plusSeconds(60);

        user.restrict(AccountStatus.SUSPENDED, expiresAt, restrictedAt);
        long restrictedVersion = user.getSecurityVersion();

        boolean normalized = user.normalizeExpiredRestriction(expiresAt);

        assertThat(restrictedVersion).isEqualTo(1L);
        assertThat(normalized).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getSecurityVersion()).isEqualTo(restrictedVersion);
    }

    @Test
    void explicitRestoreRotatesTokensAgain() {
        AppUser user = AppUser.create("user@example.com", "hash");
        Instant now = Instant.parse("2026-08-01T00:00:00Z");

        user.restrict(AccountStatus.BANNED, null, now);
        user.restore(now.plusSeconds(1));

        assertThat(user.getSecurityVersion()).isEqualTo(2L);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
