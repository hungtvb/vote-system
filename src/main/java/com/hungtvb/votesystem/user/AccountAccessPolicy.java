package com.hungtvb.votesystem.user;

import com.hungtvb.votesystem.common.error.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AccountAccessPolicy {
    private static final String UNAVAILABLE_MESSAGE = "User account is unavailable";

    private final UserRepository userRepository;

    public AccountAccessPolicy(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppUser requireActive(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(UNAVAILABLE_MESSAGE));
        requireActive(user, Instant.now());
        return user;
    }

    public void requireActive(AppUser user) {
        requireActive(user, Instant.now());
    }

    public void requireActive(AppUser user, Instant now) {
        if (user == null || !user.hasActiveAccess(now)) {
            throw new UnauthorizedException(UNAVAILABLE_MESSAGE);
        }
    }
}
