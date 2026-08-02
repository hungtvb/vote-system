package com.hungtvb.votesystem.moderation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findAllByCaseIdOrderByCreatedAtAscIdAsc(UUID caseId);
    @Query("""
            select report
              from Report report
             where report.reporterId = :reporterId
               and report.targetType = :targetType
               and report.targetId = :targetId
               and report.reasonCode = :reasonCode
               and report.status = com.hungtvb.votesystem.moderation.ReportStatus.OPEN
            """)
    Optional<Report> findActiveDuplicate(@Param("reporterId") UUID reporterId,
                                         @Param("targetType") ModerationTargetType targetType,
                                         @Param("targetId") UUID targetId,
                                         @Param("reasonCode") ReportReasonCode reasonCode);

    @Query(value = """
            select *
              from reports
             where reporter_id = :reporterId
               and (
                    cast(:beforeCreatedAt as timestamptz) is null
                    or created_at < :beforeCreatedAt
                    or (created_at = :beforeCreatedAt and id < :beforeId)
               )
             order by created_at desc, id desc
            """, nativeQuery = true)
    List<Report> findReporterHistory(@Param("reporterId") UUID reporterId,
                                     @Param("beforeCreatedAt") Instant beforeCreatedAt,
                                     @Param("beforeId") UUID beforeId,
                                     Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Report report
               set report.status = :status,
                   report.closedAt = :closedAt
             where report.caseId = :caseId
               and report.status = com.hungtvb.votesystem.moderation.ReportStatus.OPEN
            """)
    int closeOpenReports(@Param("caseId") UUID caseId,
                         @Param("status") ReportStatus status,
                         @Param("closedAt") Instant closedAt);
}
