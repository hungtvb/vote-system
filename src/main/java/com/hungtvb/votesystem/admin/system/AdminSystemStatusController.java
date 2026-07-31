package com.hungtvb.votesystem.admin.system;

import com.hungtvb.votesystem.observability.RequestLatencyLoggingFilter;
import com.hungtvb.votesystem.system.SystemStatusService;
import com.hungtvb.votesystem.system.dto.AdminSystemStatusResponse;
import com.hungtvb.votesystem.system.dto.UpdateSystemStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemStatusController {
    private final SystemStatusService service;

    public AdminSystemStatusController(SystemStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public AdminSystemStatusResponse status() {
        return service.adminStatus();
    }

    @PutMapping("/status")
    public AdminSystemStatusResponse update(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody UpdateSystemStatusRequest request,
                                            HttpServletRequest servletRequest) {
        return service.update(
                UUID.fromString(jwt.getSubject()),
                request,
                RequestLatencyLoggingFilter.requestId(servletRequest)
        );
    }
}
