package com.hungtvb.votesystem.auth;

import com.hungtvb.votesystem.auth.dto.AuthResponse;
import com.hungtvb.votesystem.auth.dto.LoginRequest;
import com.hungtvb.votesystem.auth.dto.RegisterRequest;
import com.hungtvb.votesystem.auth.session.RefreshGrant;
import com.hungtvb.votesystem.auth.session.RefreshSessionService;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.security.AuthenticatedUser;
import com.hungtvb.votesystem.security.TokenService;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshSessionService refreshSessionService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       TokenService tokenService,
                       RefreshSessionService refreshSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
    }

    @Transactional
    public IssuedAuthSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        AppUser saved = userRepository.saveAndFlush(
                AppUser.create(email, passwordEncoder.encode(request.password()))
        );
        return issueSession(AuthenticatedUser.from(saved));
    }

    @Transactional
    public IssuedAuthSession login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password())
        );
        return issueSession((AuthenticatedUser) authentication.getPrincipal());
    }

    @Transactional
    public IssuedAuthSession refresh(String refreshToken) {
        return response(refreshSessionService.rotate(refreshToken));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshSessionService.revoke(refreshToken);
    }

    @Transactional
    public int logoutAll(UUID userId) {
        return refreshSessionService.revokeAll(userId);
    }

    private IssuedAuthSession issueSession(AuthenticatedUser user) {
        return response(refreshSessionService.issue(user));
    }

    private IssuedAuthSession response(RefreshGrant grant) {
        AuthenticatedUser user = grant.user();
        AuthResponse response = new AuthResponse(
                "Bearer",
                tokenService.issue(user),
                tokenService.expiresInSeconds(),
                grant.expiresInSeconds(),
                user.id(),
                user.email(),
                user.role().name()
        );
        return new IssuedAuthSession(response, grant.refreshToken());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
