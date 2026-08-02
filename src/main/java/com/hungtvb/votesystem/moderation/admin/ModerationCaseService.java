package com.hungtvb.votesystem.moderation.admin;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.admin.search.dto.AdminPageResponse;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.moderation.ModerationCase;
import com.hungtvb.votesystem.moderation.ModerationCaseRepository;
import com.hungtvb.votesystem.moderation.ModerationCaseStatus;
import com.hungtvb.votesystem.moderation.ModerationResolutionAction;
import com.hungtvb.votesystem.moderation.ModerationTargetService;
import com.hungtvb.votesystem.moderation.ModerationTargetType;
import com.hungtvb.votesystem.moderation.ReportRepository;
import com.hungtvb.votesystem.moderation.ReportStatus;
import com.hungtvb.votesystem.moderation.TargetValidationStatus;
import com.hungtvb.votesystem.moderation.admin.dto.AdminReportResponse;
import com.hungtvb.votesystem.moderation.admin.dto.ModerationCaseDetailResponse;
import com.hungtvb.votesystem.moderation.admin.dto.ModerationCaseResponse;
import com.hungtvb.votesystem.moderation.admin.dto.ResolveModerationCaseRequest;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ModerationCaseService {
    private final EntityManager entityManager;
    private final ModerationCaseRepository caseRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ModerationTargetService targetService;
    private final AdminAuditLogService auditLogService;

    public ModerationCaseService(EntityManager entityManager,
                                 ModerationCaseRepository caseRepository,
                                 ReportRepository reportRepository,
                                 UserRepository userRepository,
                                 ModerationTargetService targetService,
                                 AdminAuditLogService auditLogService) {
        this.entityManager = entityManager;
        this.caseRepository = caseRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.targetService = targetService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<ModerationCaseResponse> list(ModerationCaseStatus status,
                                                          ModerationTargetType targetType,
                                                          UUID assigneeId,
                                                          int page,
                                                          int size) {
        Page<ModerationCaseResponse> result = caseRepository.findAllFiltered(
                        status,
                        targetType,
                        assigneeId,
                        PageRequest.of(page, size, Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.desc("id")
                        )))
                .map(ModerationCaseResponse::from);
        return AdminPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ModerationCaseDetailResponse detail(UUID caseId) {
        ModerationCase moderationCase = find(caseId);
        return new ModerationCaseDetailResponse(
                ModerationCaseResponse.from(moderationCase),
                reportRepository.findAllByCaseIdOrderByCreatedAtAscIdAsc(caseId)
                        .stream()
                        .map(AdminReportResponse::from)
                        .toList()
        );
    }

    @Transactional
    public ModerationCaseResponse assign(UUID actorId, UUID caseId, UUID requestedAssigneeId, String rawReason) {
        ModerationCase moderationCase = lock(caseId);
        UUID assigneeId = requestedAssigneeId == null ? actorId : requestedAssigneeId;
        requireActiveAdmin(assigneeId);
        String reason = normalizeReason(rawReason);
        ModerationCaseStatus previousStatus = moderationCase.getStatus();
        UUID previousAssignee = moderationCase.getAssigneeId();
        if (!moderationCase.assign(assigneeId, Instant.now())) {
            return ModerationCaseResponse.from(moderationCase);
        }
        Map<String, String> metadata = baseMetadata(moderationCase, previousStatus);
        metadata.put("previous_assignee", previousAssignee == null ? "unassigned" : previousAssignee.toString());
        metadata.put("new_assignee", assigneeId.toString());
        appendAudit(actorId, AdminAuditAction.ADMIN_ASSIGN_MODERATION_CASE, moderationCase, reason, metadata);
        return ModerationCaseResponse.from(moderationCase);
    }

    @Transactional
    public ModerationCaseResponse triage(UUID actorId, UUID caseId, String rawReason) {
        ModerationCase moderationCase = lock(caseId);
        String reason = normalizeReason(rawReason);
        ModerationCaseStatus previousStatus = moderationCase.getStatus();
        try {
            if (!moderationCase.triage(Instant.now())) {
                return ModerationCaseResponse.from(moderationCase);
            }
        } catch (IllegalStateException exception) {
            throw stateConflict(exception);
        }
        appendAudit(actorId, AdminAuditAction.ADMIN_TRIAGE_MODERATION_CASE,
                moderationCase, reason, baseMetadata(moderationCase, previousStatus));
        return ModerationCaseResponse.from(moderationCase);
    }

    @Transactional
    public ModerationCaseResponse beginReview(UUID actorId, UUID caseId, String rawReason) {
        ModerationCase moderationCase = lock(caseId);
        String reason = normalizeReason(rawReason);
        ModerationCaseStatus previousStatus = moderationCase.getStatus();
        try {
            if (!moderationCase.beginReview(Instant.now())) {
                return ModerationCaseResponse.from(moderationCase);
            }
        } catch (IllegalStateException exception) {
            throw stateConflict(exception);
        }
        appendAudit(actorId, AdminAuditAction.ADMIN_REVIEW_MODERATION_CASE,
                moderationCase, reason, baseMetadata(moderationCase, previousStatus));
        return ModerationCaseResponse.from(moderationCase);
    }

    @Transactional
    public ModerationCaseResponse resolve(UUID actorId, UUID caseId, ResolveModerationCaseRequest request) {
        ModerationCase moderationCase = lock(caseId);
        String reason = normalizeReason(request.reason());
        Instant until = normalizeUntil(request.until());
        validateResolutionRequest(moderationCase, request.action(), until);
        if (moderationCase.isSameResolution(request.action(), reason, until)) {
            return ModerationCaseResponse.from(moderationCase);
        }
        ModerationCaseStatus previousStatus = moderationCase.getStatus();
        if (previousStatus != ModerationCaseStatus.IN_REVIEW) {
            throw new ConflictException("Only moderation cases in review can be resolved");
        }

        targetService.applyResolution(
                actorId,
                moderationCase.getTargetType(),
                moderationCase.getTargetId(),
                request.action(),
                reason,
                until
        );

        // Some target adapters execute bulk updates with clearAutomatically=true.
        // Reacquire the locked case before applying the workflow transition so a
        // cleared persistence context cannot leave the target action committed
        // while the case and reports remain open.
        moderationCase = lock(caseId);
        if (moderationCase.getStatus() != ModerationCaseStatus.IN_REVIEW) {
            throw new ConflictException("Moderation case changed while applying the resolution action");
        }

        Instant now = Instant.now();
        moderationCase.resolve(request.action(), reason, until, now);
        reportRepository.closeOpenReports(caseId, ReportStatus.RESOLVED, now);

        Map<String, String> metadata = baseMetadata(moderationCase, previousStatus);
        metadata.put("resolution_action", request.action().name());
        metadata.put("resolution_until", until == null ? "not_set" : until.toString());
        appendAudit(actorId, AdminAuditAction.ADMIN_RESOLVE_MODERATION_CASE,
                moderationCase, reason, metadata);
        return ModerationCaseResponse.from(moderationCase);
    }

    @Transactional
    public ModerationCaseResponse reject(UUID actorId, UUID caseId, String rawReason) {
        ModerationCase moderationCase = lock(caseId);
        String reason = normalizeReason(rawReason);
        if (moderationCase.isSameRejection(reason)) {
            return ModerationCaseResponse.from(moderationCase);
        }
        ModerationCaseStatus previousStatus = moderationCase.getStatus();
        if (previousStatus != ModerationCaseStatus.IN_REVIEW) {
            throw new ConflictException("Only moderation cases in review can be rejected");
        }
        Instant now = Instant.now();
        moderationCase.reject(reason, now);
        reportRepository.closeOpenReports(caseId, ReportStatus.REJECTED, now);
        appendAudit(actorId, AdminAuditAction.ADMIN_REJECT_MODERATION_CASE,
                moderationCase, reason, baseMetadata(moderationCase, previousStatus));
        return ModerationCaseResponse.from(moderationCase);
    }

    @Transactional
    public ModerationCaseResponse reopen(UUID actorId, UUID caseId, String rawReason) {
        ModerationCase snapshot = find(caseId);
        lockTarget(snapshot.getTargetType(), snapshot.getTargetId());
        ModerationCase moderationCase = lock(caseId);
        caseRepository.findActiveByTarget(moderationCase.getTargetType(), moderationCase.getTargetId())
                .filter(active -> !active.getId().equals(caseId))
                .ifPresent(active -> {
                    throw new ConflictException("Another active moderation case already exists for this target");
                });
        String reason = normalizeReason(rawReason);
        ModerationCaseStatus previousStatus = moderationCase.getStatus();
        try {
            if (!moderationCase.reopen(Instant.now())) {
                return ModerationCaseResponse.from(moderationCase);
            }
        } catch (IllegalStateException exception) {
            throw stateConflict(exception);
        }
        appendAudit(actorId, AdminAuditAction.ADMIN_REOPEN_MODERATION_CASE,
                moderationCase, reason, baseMetadata(moderationCase, previousStatus));
        return ModerationCaseResponse.from(moderationCase);
    }


    private void lockTarget(ModerationTargetType targetType, UUID targetId) {
        String lockKey = "moderation-case:" + targetType.name() + ":" + targetId;
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(:lockKey as text), 0))")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }

    private ModerationCase find(UUID caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Moderation case not found"));
    }

    private ModerationCase lock(UUID caseId) {
        return caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Moderation case not found"));
    }

    private void requireActiveAdmin(UUID assigneeId) {
        AppUser assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));
        if (assignee.getRole() != Role.ADMIN
                || assignee.effectiveAccountStatus(Instant.now()) != AccountStatus.ACTIVE) {
            throw new InvalidRequestException("Moderation cases can only be assigned to active administrators");
        }
    }

    private void validateResolutionRequest(ModerationCase moderationCase,
                                           ModerationResolutionAction action,
                                           Instant until) {
        if (moderationCase.getTargetValidationStatus() != TargetValidationStatus.VERIFIED) {
            throw new ConflictException("Moderation target validation is deferred");
        }
        if (action.targetType() != moderationCase.getTargetType()) {
            throw new InvalidRequestException("Resolution action is incompatible with the moderation target");
        }
        if (moderationCase.getTargetType() == ModerationTargetType.BALLOT && until != null) {
            throw new InvalidRequestException("Ballot moderation actions do not accept an expiry");
        }
        if (action == ModerationResolutionAction.RESTORE_USER && until != null) {
            throw new InvalidRequestException("Restore user does not accept an expiry");
        }
    }

    private Instant normalizeUntil(Instant until) {
        return until == null ? null : until.truncatedTo(ChronoUnit.MICROS);
    }

    private String normalizeReason(String rawReason) {
        String reason = rawReason == null ? "" : rawReason.strip();
        if (reason.isBlank() || reason.length() > 500) {
            throw new InvalidRequestException("Moderation reason is invalid");
        }
        return reason;
    }

    private Map<String, String> baseMetadata(ModerationCase moderationCase,
                                             ModerationCaseStatus previousStatus) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("target_type", moderationCase.getTargetType().name());
        metadata.put("target_id", moderationCase.getTargetId().toString());
        metadata.put("previous_status", previousStatus.name());
        metadata.put("new_status", moderationCase.getStatus().name());
        metadata.put("report_count", Integer.toString(moderationCase.getReportCount()));
        return metadata;
    }

    private void appendAudit(UUID actorId,
                             AdminAuditAction action,
                             ModerationCase moderationCase,
                             String reason,
                             Map<String, String> metadata) {
        auditLogService.append(new AdminAuditEvent(
                actorId,
                action,
                AdminAuditTargetType.MODERATION_CASE,
                moderationCase.getId().toString(),
                reason,
                metadata
        ));
    }

    private ConflictException stateConflict(IllegalStateException exception) {
        return new ConflictException(exception.getMessage());
    }
}
