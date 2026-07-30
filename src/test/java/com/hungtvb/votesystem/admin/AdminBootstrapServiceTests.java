package com.hungtvb.votesystem.admin;

import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTests {

    @Mock UserRepository userRepository;
    @Mock AdminBootstrapService bootstrapService;

    @Test
    void localAndSocialOnboardingAlwaysCreateUserRole() {
        assertEquals(Role.USER, AppUser.create("local@example.com", "hash").getRole());
        assertEquals(Role.USER, AppUser.createSocial("social@example.com", "Social Voter").getRole());
    }

    @Test
    void promotesExistingNormalizedAccountAndIsIdempotent() {
        AdminBootstrapService service = new AdminBootstrapService(userRepository);
        AppUser user = AppUser.create("admin@example.com", "hash");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        assertTrue(service.promoteExistingUser("  ADMIN@EXAMPLE.COM "));
        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository).save(user);

        assertFalse(service.promoteExistingUser("admin@example.com"));
        verify(userRepository).save(user);
    }

    @Test
    void rejectsRestrictedBootstrapTargetWithoutExposingAccountDetails() {
        AdminBootstrapService service = new AdminBootstrapService(userRepository);
        AppUser user = AppUser.create("restricted@example.com", "hash");
        user.restrict(AccountStatus.SUSPENDED, null, Instant.now());
        when(userRepository.findByEmail("restricted@example.com")).thenReturn(Optional.of(user));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.promoteExistingUser("restricted@example.com")
        );

        assertEquals("Configured admin bootstrap target is unavailable", error.getMessage());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failsWithoutExposingConfiguredTargetWhenAccountIsMissing() {
        AdminBootstrapService service = new AdminBootstrapService(userRepository);
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.promoteExistingUser("missing@example.com")
        );

        assertEquals("Configured admin bootstrap target was not found", error.getMessage());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidBootstrapConfigurationBeforeRepositoryLookup() {
        AdminBootstrapService service = new AdminBootstrapService(userRepository);

        assertThrows(IllegalStateException.class, () -> service.promoteExistingUser("  "));
        assertThrows(IllegalStateException.class, () -> service.promoteExistingUser(null));
        verifyNoInteractions(userRepository);
    }

    @Test
    void disabledRunnerDoesNotInvokeBootstrapService() throws Exception {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                new AdminBootstrapProperties(false, "admin@example.com"),
                bootstrapService
        );

        runner.run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(bootstrapService);
    }
}
