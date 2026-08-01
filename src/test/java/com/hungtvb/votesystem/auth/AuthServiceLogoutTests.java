package com.hungtvb.votesystem.auth;

import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.auth.session.RefreshSessionRepository;
import com.hungtvb.votesystem.auth.session.RefreshSessionService;
import com.hungtvb.votesystem.auth.social.UserIdentityRepository;
import com.hungtvb.votesystem.security.TokenService;
import com.hungtvb.votesystem.user.AccountAccessPolicy;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceLogoutTests {

    @Test
    void activeRefreshSessionLogoutRotatesAccessTokenVersion() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.create("user@example.com", "hash");
        UserRepository users = mock(UserRepository.class);
        RefreshSessionService refreshSessions = mock(RefreshSessionService.class);
        when(refreshSessions.revoke("active-token")).thenReturn(Optional.of(userId));
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        AuthService service = service(users, refreshSessions);

        service.logout("active-token");

        assertThat(user.getSecurityVersion()).isEqualTo(1L);
    }

    @Test
    void missingOrPreviouslyRevokedSessionCannotRotateAccessTokensAgain() {
        UserRepository users = mock(UserRepository.class);
        RefreshSessionService refreshSessions = mock(RefreshSessionService.class);
        when(refreshSessions.revoke("stale-token")).thenReturn(Optional.empty());
        AuthService service = service(users, refreshSessions);

        service.logout("stale-token");

        verifyNoInteractions(users);
    }

    private AuthService service(UserRepository users, RefreshSessionService refreshSessions) {
        return new AuthService(
                users,
                mock(UserIdentityRepository.class),
                mock(PasswordEncoder.class),
                mock(AuthenticationManager.class),
                mock(TokenService.class),
                mock(RefreshSessionRepository.class),
                refreshSessions,
                mock(AccountAccessPolicy.class),
                mock(AuthRestoreMetrics.class)
        );
    }
}
