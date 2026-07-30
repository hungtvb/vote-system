package com.hungtvb.votesystem.admin.audit;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuditLogStore {
    private final EntityManager entityManager;

    public AdminAuditLogStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    AdminAuditLog append(AdminAuditLog auditLog) {
        entityManager.persist(auditLog);
        return auditLog;
    }
}
