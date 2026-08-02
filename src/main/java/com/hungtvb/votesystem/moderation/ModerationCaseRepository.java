package com.hungtvb.votesystem.moderation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModerationCaseRepository extends JpaRepository<ModerationCase, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select moderationCase from ModerationCase moderationCase where moderationCase.id = :id")
    Optional<ModerationCase> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select moderationCase
              from ModerationCase moderationCase
             where moderationCase.targetType = :targetType
               and moderationCase.targetId = :targetId
               and moderationCase.status in (
                    com.hungtvb.votesystem.moderation.ModerationCaseStatus.OPEN,
                    com.hungtvb.votesystem.moderation.ModerationCaseStatus.TRIAGED,
                    com.hungtvb.votesystem.moderation.ModerationCaseStatus.IN_REVIEW,
                    com.hungtvb.votesystem.moderation.ModerationCaseStatus.REOPENED
               )
            """)
    Optional<ModerationCase> findActiveByTarget(@Param("targetType") ModerationTargetType targetType,
                                                @Param("targetId") UUID targetId);

    @Query("""
            select moderationCase
              from ModerationCase moderationCase
             where (:status is null or moderationCase.status = :status)
               and (:targetType is null or moderationCase.targetType = :targetType)
               and (:assigneeId is null or moderationCase.assigneeId = :assigneeId)
            """)
    Page<ModerationCase> findAllFiltered(@Param("status") ModerationCaseStatus status,
                                         @Param("targetType") ModerationTargetType targetType,
                                         @Param("assigneeId") UUID assigneeId,
                                         Pageable pageable);
}
