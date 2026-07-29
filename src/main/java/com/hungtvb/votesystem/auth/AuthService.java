package com.hungtvb.votesystem.auth;

import com.hungtvb.votesystem.auth.dto.AuthResponse;
import com.hungtvb.votesystem.auth.dto.LoginRequest;
import com.hungtvb.votesystem.auth.dto.RegisterRequest;
import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.auth.session.RefreshGrant;
import com.hungtvb.votesystem.auth.session.RefreshSessionService;
import com.hungtvb.votesystem.auth.social.UserIdentity;
import com.hungtvb.votesystem.auth.social.UserIdentityRepository;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.security.AuthenticatedUser;
import com.hungtvb.votesystem.security.TokenService;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import com.hungtvb.votesystem.user.dto.UserProfileResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshSessionService refreshSessionService;
    private final AuthRestoreMetrics metrics;

    public AuthService(UserRepository userRepository,
                       UserIdentityRepository identityRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       TokenService tokenService,
                       RefreshSessionService refreshSessionService,
                       AuthRestoreMetrics metrics) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
        this.metrics = metrics;
    }

    @Transactional
    public IssuedAuthSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        AppUser saved = userRepository.saveAndFlush(
                AppUser.create(email, request.displayName(), passwordEncoder.encode(request.password()))
        );
        return issueSessionWithinTransaction(saved, false);
    }

    @Transactional
    public IssuedAuthSession login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password())
        );
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> new UnauthorizedException("User account is unavailable"));
        return issueSessionWithinTransaction(user, false);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedAuthSession refresh(String refreshToken) {
        return response(refreshSessionService.rotate(refreshToken), true);
    }

    public void logout(String refreshToken) {
        refreshSessionService.revoke(refreshToken);
    }

    public int logoutAll(UUID userId) {
        return refreshSessionService.revokeAll(userId);
    }

    @Transactional
    public IssuedAuthSession issueSession(AppUser user) {
        return issueSessionWithinTransaction(user, false);
    }

    private IssuedAuthSession issueSessionWithinTransaction(AppUser user, boolean measureRefresh) {
        return response(refreshSessionService.issue(user), measureRefresh);
    }

    private IssuedAuthSession response(RefreshGrant grant, boolean measureRefresh) {
        AppUser user = grant.user();
        List<UserIdentity> identities = measureRefresh
                ? metrics.timeStage(
                        AuthRestoreMetrics.OPERATION_REFRESH,
                        "identity_lookup",
                        () -> identityRepository.findAllByUserId(user.getId())
                )
                : identityRepository.findAllByUserId(user.getId());
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
        String accessToken = measureRefresh
                ? metrics.timeStage(
                        AuthRestoreMetrics.OPERATION_REFRESH,
                        "jwt_issue",
                        () -> tokenService.issue(authenticatedUser)
                )
                : tokenService.issue(authenticatedUser);
        UserProfileResponse profile = UserProfileResponse.from(user, identities);
        AuthResponse response = new AuthResponse(
                "Bearer",
                accessToken,
                tokenService.expiresInSeconds(),
                grant.expiresInSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                profile
        );
        return new IssuedAuthSession(response, grant.refreshToken());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
