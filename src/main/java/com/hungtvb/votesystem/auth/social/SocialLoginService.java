package com.hungtvb.votesystem.auth.social;

import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class SocialLoginService {
    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;

    public SocialLoginService(EntityManager entityManager,
                              UserRepository userRepository,
                              UserIdentityRepository identityRepository) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public AppUser complete(SocialProfile profile, SocialAuthContext context) {
        lockIdentity(profile.provider(), profile.subject());

        UserIdentity existingIdentity = identityRepository
                .findByProviderAndProviderSubject(profile.provider(), profile.subject())
                .orElse(null);

        if (context.intent() == SocialIntent.LINK_ACCOUNT) {
            return link(profile, context.linkUserId(), existingIdentity);
        }

        if (existingIdentity != null) {
            return existingIdentity.getUser();
        }

        String verifiedEmail = profile.emailVerified() ? normalizeEmail(profile.email()) : null;
        if (verifiedEmail != null && userRepository.findByEmail(verifiedEmail).isPresent()) {
            throw new SocialLoginException(
                    "account_link_required",
                    "An account already owns this verified email. Sign in to that account and link the provider.");
        }

        AppUser user = userRepository.saveAndFlush(
                AppUser.createSocial(verifiedEmail, profile.displayName()));
        identityRepository.saveAndFlush(UserIdentity.create(
                user,
                profile.provider(),
                profile.subject(),
                normalizeEmail(profile.email()),
                profile.emailVerified()));
        return user;
    }

    private AppUser link(SocialProfile profile, UUID linkUserId, UserIdentity existingIdentity) {
        if (linkUserId == null) {
            throw new SocialLoginException("invalid_link_context", "Social account link context is missing");
        }
        if (existingIdentity != null) {
            if (existingIdentity.getUser().getId().equals(linkUserId)) {
                return existingIdentity.getUser();
            }
            throw new SocialLoginException(
                    "identity_already_linked",
                    "This provider identity is already linked to another Vote System account.");
        }

        AppUser user = userRepository.findByIdForUpdate(linkUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (identityRepository.existsByUserIdAndProvider(linkUserId, profile.provider())) {
            throw new SocialLoginException(
                    "provider_already_linked",
                    "This Vote System account already has a linked identity for that provider.");
        }

        String verifiedEmail = profile.emailVerified() ? normalizeEmail(profile.email()) : null;
        if (verifiedEmail != null) {
            userRepository.findByEmail(verifiedEmail)
                    .filter(owner -> !owner.getId().equals(linkUserId))
                    .ifPresent(owner -> {
                        throw new SocialLoginException(
                                "email_owned_by_another_account",
                                "The provider email belongs to another Vote System account.");
                    });
        }

        identityRepository.saveAndFlush(UserIdentity.create(
                user,
                profile.provider(),
                profile.subject(),
                normalizeEmail(profile.email()),
                profile.emailVerified()));
        return user;
    }

    private void lockIdentity(SocialProvider provider, String subject) {
        String key = provider.name() + ':' + subject;
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(cast(:identityKey as text)))")
                .setParameter("identityKey", key)
                .getSingleResult();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank()
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }
}
