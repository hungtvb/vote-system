package com.hungtvb.votesystem.auth.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    @Query("select s.userId from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("""
            select s
              from RefreshSession s
             where s.userId = :userId
               and s.revokedAt is null
             order by s.startedAt desc, s.familyId
            """)
    List<RefreshSession> findAllActiveByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
              from RefreshSession s
             where s.userId = :userId
               and s.revokedAt is null
             order by s.id
            """)
    List<RefreshSession> findAllActiveByUserIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
              from RefreshSession s
             where s.userId = :userId
               and s.familyId = :familyId
               and s.revokedAt is null
             order by s.id
            """)
    List<RefreshSession> findActiveFamilyForUpdate(@Param("userId") UUID userId,
                                                    @Param("familyId") UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
              from RefreshSession s
             where s.userId = :userId
               and s.familyId <> :currentFamilyId
               and s.revokedAt is null
             order by s.id
            """)
    List<RefreshSession> findOtherActiveFamiliesForUpdate(@Param("userId") UUID userId,
                                                          @Param("currentFamilyId") UUID currentFamilyId);
}
