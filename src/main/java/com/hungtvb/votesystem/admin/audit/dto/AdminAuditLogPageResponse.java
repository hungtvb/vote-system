package com.hungtvb.votesystem.admin.audit.dto;

import com.hungtvb.votesystem.admin.audit.AdminAuditLog;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdminAuditLogPageResponse(
        List<AdminAuditLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminAuditLogPageResponse from(Page<AdminAuditLog> result) {
        return new AdminAuditLogPageResponse(
                result.getContent().stream().map(AdminAuditLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
