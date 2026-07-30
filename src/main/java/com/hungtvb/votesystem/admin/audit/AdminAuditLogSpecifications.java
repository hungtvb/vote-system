package com.hungtvb.votesystem.admin.audit;

import org.springframework.data.jpa.domain.Specification;

public final class AdminAuditLogSpecifications {
    private AdminAuditLogSpecifications() {
    }

    public static Specification<AdminAuditLog> matches(AdminAuditLogFilter filter) {
        Specification<AdminAuditLog> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.conjunction();

        if (filter.action() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("action"), filter.action()));
        }
        if (filter.actorId() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("actorId"), filter.actorId()));
        }
        if (filter.targetType() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("targetType"), filter.targetType()));
        }
        if (filter.targetId() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("targetId"), filter.targetId()));
        }
        return specification;
    }
}
