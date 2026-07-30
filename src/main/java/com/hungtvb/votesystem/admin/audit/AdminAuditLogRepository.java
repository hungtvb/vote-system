package com.hungtvb.votesystem.admin.audit;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AdminAuditLogRepository
        extends Repository<AdminAuditLog, UUID>, JpaSpecificationExecutor<AdminAuditLog> {
}
