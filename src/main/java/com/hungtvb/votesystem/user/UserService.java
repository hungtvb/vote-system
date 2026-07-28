package com.hungtvb.votesystem.user;

import com.hungtvb.votesystem.auth.metrics.AuthRestoreMetrics;
import com.hungtvb.votesystem.auth.social.UserIdentity;
import com.hungtvb.votesystem.auth.social.UserIdentityRepository;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.user.dto.PublicUserProfileResponse;
import com.hungtvb.votesystem.user.dto.UpdateUserProfileRequest;
import com.hungtvb.votesystem.user.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final AuthRestoreMetrics metrics;

    public UserService(UserRepository userRepository,
                       UserIdentityRepository identityRepository,
                       AuthRestoreMetrics metrics) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(UUID userId) {
        AuthRestoreMetrics.RestoreSample sample = metrics.startTotal(AuthRestoreMetrics.OPERATION_PROFILE);
        String outcome = "error";
        try {
            UserProfileResponse response = privateProfile(findUser(userId));
            outcome = "success";
            return response;
        } finally {
            metrics.stopTotal(sample, outcome);
        }
    }

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicUser(UUID userId) {
        return PublicUserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateCurrentUser(UUID userId, UpdateUserProfileRequest request) {
        AppUser user = findUser(userId);
        user.updateProfile(
                request.displayName(),
                request.bio(),
                request.avatarIcon(),
                request.avatarColor(),
                request.preferredLocale()
        );
        return privateProfile(user);
    }

    private AppUser findUser(UUID userId) {
        return metrics.timeStage(
                AuthRestoreMetrics.OPERATION_PROFILE,
                "user_lookup",
                () -> userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"))
        );
    }

    private UserProfileResponse privateProfile(AppUser user) {
        List<UserIdentity> identities = metrics.timeStage(
                AuthRestoreMetrics.OPERATION_PROFILE,
                "identity_lookup",
                () -> identityRepository.findAllByUserId(user.getId())
        );
        return UserProfileResponse.from(user, identities);
    }
}
