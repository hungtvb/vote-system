package com.hungtvb.votesystem.moderation;

import com.hungtvb.votesystem.moderation.dto.CreateReportRequest;
import com.hungtvb.votesystem.moderation.dto.ReportHistoryResponse;
import com.hungtvb.votesystem.moderation.dto.ReportResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody CreateReportRequest request) {
        return service.create(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/mine")
    public ReportHistoryResponse history(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) Instant beforeCreatedAt,
                                         @RequestParam(required = false) UUID beforeId,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.history(UUID.fromString(jwt.getSubject()), beforeCreatedAt, beforeId, limit);
    }
}
