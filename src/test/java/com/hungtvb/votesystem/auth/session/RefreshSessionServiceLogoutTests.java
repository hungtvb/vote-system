package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.common.config.RefreshTokenProperties;
import com.hungtvb.votesystem.user.AccountAccessPolicy;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshSessionServiceLogoutTests {

    @Test
    void activeRefreshSessionReturnsOwnerOnlyOnFirstRevoke() {
        UUID userId = UUID.randomUUID();
        RefreshSession session = RefreshSession.create(
                userId,
                "hash",
                Instant.now(),
                Instant.now().plus(Duration.ofDays(1))
        );
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        when(repository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(userId));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));
        RefreshSessionService service = service(repository, userId);

        Optional<UUID> first = service.revoke("raw-token");
        Optional<UUID> second = service.revoke("raw-token");

        assertThat(first).contains(userId);
        assertThat(second).isEmpty();
        assertThat(session.isRevoked()).isTrue();
    }

    @Test
    void expiredRefreshSessionDoesNotAuthorizeAccessTokenRotation() {
        UUID userId = UUID.randomUUID();
        RefreshSession session = RefreshSession.create(
                userId,
                "hash",
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(1))
        );
        RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
        when(repository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(userId));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));
        RefreshSessionService service = service(repository, userId);

        Optional<UUID> result = service.revoke("expired-token");

        assertThat(result).isEmpty();
        assertThat(session.isRevoked()).isTrue();
    }

    private RefreshSessionService service(RefreshSessionRepository repository, UUID userId) {
        UserRepository users = mock(UserRepository.class);
        when(users.findByIdForUpdate(any())).thenReturn(Optional.of(AppUser.create("user@example.com", "hash")));
        return new RefreshSessionService(
                repository,
                users,
                new RefreshTokenProperties(Duration.ofDays(30), "vote_refresh", false, "Lax"),
                mock(AccountAccessPolicy.class),
                mock(AuthRestoreMetrics.class),
                mock(AccountSecurityEventService.class)
        );
    }
}
