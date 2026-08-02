package com.hungtvb.votesystem.moderation.admin;

import com.hungtvb.votesystem.admin.search.dto.AdminPageResponse;
import com.hungtvb.votesystem.moderation.ModerationCaseStatus;
import com.hungtvb.votesystem.moderation.ModerationTargetType;
import com.hungtvb.votesystem.moderation.admin.dto.AssignModerationCaseRequest;
import com.hungtvb.votesystem.moderation.admin.dto.ModerationCaseDetailResponse;
import com.hungtvb.votesystem.moderation.admin.dto.ModerationCaseReasonRequest;
import com.hungtvb.votesystem.moderation.admin.dto.ModerationCaseResponse;
import com.hungtvb.votesystem.moderation.admin.dto.ResolveModerationCaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/moderation-cases")
@PreAuthorize("hasRole('ADMIN')")
public class ModerationCaseController {
    private final ModerationCaseService service;

    public ModerationCaseController(ModerationCaseService service) {
        this.service = service;
    }

    @GetMapping
    public AdminPageResponse<ModerationCaseResponse> list(
            @RequestParam(required = false) ModerationCaseStatus status,
            @RequestParam(required = false) ModerationTargetType targetType,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, targetType, assigneeId, page, size);
    }

    @GetMapping("/{caseId}")
    public ModerationCaseDetailResponse detail(@PathVariable UUID caseId) {
        return service.detail(caseId);
    }

    @PostMapping("/{caseId}/assign")
    public ModerationCaseResponse assign(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID caseId,
                                         @Valid @RequestBody AssignModerationCaseRequest request) {
        return service.assign(actorId(jwt), caseId, request.assigneeId(), request.reason());
    }

    @PostMapping("/{caseId}/triage")
    public ModerationCaseResponse triage(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID caseId,
                                         @Valid @RequestBody ModerationCaseReasonRequest request) {
        return service.triage(actorId(jwt), caseId, request.reason());
    }

    @PostMapping("/{caseId}/review")
    public ModerationCaseResponse beginReview(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID caseId,
                                              @Valid @RequestBody ModerationCaseReasonRequest request) {
        return service.beginReview(actorId(jwt), caseId, request.reason());
    }

    @PostMapping("/{caseId}/resolve")
    public ModerationCaseResponse resolve(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID caseId,
                                          @Valid @RequestBody ResolveModerationCaseRequest request) {
        return service.resolve(actorId(jwt), caseId, request);
    }

    @PostMapping("/{caseId}/reject")
    public ModerationCaseResponse reject(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID caseId,
                                         @Valid @RequestBody ModerationCaseReasonRequest request) {
        return service.reject(actorId(jwt), caseId, request.reason());
    }

    @PostMapping("/{caseId}/reopen")
    public ModerationCaseResponse reopen(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID caseId,
                                         @Valid @RequestBody ModerationCaseReasonRequest request) {
        return service.reopen(actorId(jwt), caseId, request.reason());
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
