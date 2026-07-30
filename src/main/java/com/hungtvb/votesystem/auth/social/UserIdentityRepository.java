package com.hungtvb.votesystem.auth.social;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<UserIdentity> findByProviderAndProviderSubject(SocialProvider provider, String providerSubject);

    boolean existsByUserIdAndProvider(UUID userId, SocialProvider provider);
    List<UserIdentity> findAllByUserId(UUID userId);

    @Query("""
            select identity.user.id as userId,
                   identity.provider as provider
              from UserIdentity identity
             where identity.user.id in :userIds
            """)
    List<UserProviderSummary> findProviderSummariesByUserIds(@Param("userIds") Collection<UUID> userIds);
}
