package com.hungtvb.votesystem.admin;

import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AdminBootstrapService {
    private final UserRepository userRepository;

    public AdminBootstrapService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public boolean promoteExistingUser(String configuredEmail) {
        String normalizedEmail = normalizeEmail(configuredEmail);
        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("Configured admin bootstrap target was not found"));

        if (!user.promoteToAdmin()) {
            return false;
        }

        userRepository.save(user);
        return true;
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            throw new IllegalStateException("Admin bootstrap email configuration is invalid");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 320 || !normalized.contains("@")) {
            throw new IllegalStateException("Admin bootstrap email configuration is invalid");
        }
        return normalized;
    }
}
