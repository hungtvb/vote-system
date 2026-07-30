package com.hungtvb.votesystem.admin.audit;

import com.hungtvb.votesystem.admin.audit.dto.AdminAuditLogPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {
    private final AdminAuditLogService service;

    public AdminAuditLogController(AdminAuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public AdminAuditLogPageResponse list(
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AdminAuditTargetType targetType,
            @RequestParam(required = false)
            @Size(max = 128)
            @Pattern(regexp = ".*\\S.*", message = "must contain a non-whitespace character")
            String targetId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(
                PageRequest.of(page, size),
                new AdminAuditLogFilter(action, actorId, targetType, targetId)
        );
    }
}
