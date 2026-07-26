package com.hungtvb.votesystem.auth.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByProviderAndProviderSubject(SocialProvider provider, String providerSubject);
    boolean existsByUserIdAndProvider(UUID userId, SocialProvider provider);
    List<UserIdentity> findAllByUserId(UUID userId);
}
