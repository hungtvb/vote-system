package com.hungtvb.votesystem.admin.ranking;

import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingRebuildRequest;
import com.hungtvb.votesystem.admin.ranking.dto.AdminRankingStatusResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rankings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRankingController {
    private final AdminRankingService service;

    public AdminRankingController(AdminRankingService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public AdminRankingStatusResponse status() {
        return service.status();
    }

    @PostMapping("/rebuild")
    public AdminRankingStatusResponse rebuild(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody AdminRankingRebuildRequest request) {
        return service.rebuild(UUID.fromString(jwt.getSubject()), request.reason().strip());
    }
}
