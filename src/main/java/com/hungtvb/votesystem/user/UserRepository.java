package com.hungtvb.votesystem.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select count(u)
              from AppUser u
             where u.role = com.hungtvb.votesystem.user.Role.ADMIN
               and (
                    u.accountStatus = com.hungtvb.votesystem.user.AccountStatus.ACTIVE
                    or (u.statusUntil is not null and u.statusUntil <= :now)
               )
            """)
    long countEffectiveActiveAdmins(@Param("now") Instant now);
}
