package com.hungtvb.votesystem.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AdminAuditLogRepository extends Repository<AdminAuditLog, UUID> {

    @Query("""
            select auditLog
              from AdminAuditLog auditLog
             where (:action is null or auditLog.action = :action)
               and (:actorId is null or auditLog.actorId = :actorId)
               and (:targetType is null or auditLog.targetType = :targetType)
               and (:targetId is null or auditLog.targetId = :targetId)
            """)
    Page<AdminAuditLog> findAllFiltered(@Param("action") AdminAuditAction action,
                                        @Param("actorId") UUID actorId,
                                        @Param("targetType") AdminAuditTargetType targetType,
                                        @Param("targetId") String targetId,
                                        Pageable pageable);
}
