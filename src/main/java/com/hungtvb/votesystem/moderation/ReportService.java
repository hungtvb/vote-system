package com.hungtvb.votesystem.moderation;

import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.moderation.dto.CreateReportRequest;
import com.hungtvb.votesystem.moderation.dto.ReportHistoryResponse;
import com.hungtvb.votesystem.moderation.dto.ReportResponse;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private static final int MAX_HISTORY_LIMIT = 100;

    private final EntityManager entityManager;
    private final ModerationTargetService targetService;
    private final ModerationCaseRepository caseRepository;
    private final ReportRepository reportRepository;

    public ReportService(EntityManager entityManager,
                         ModerationTargetService targetService,
                         ModerationCaseRepository caseRepository,
                         ReportRepository reportRepository) {
        this.entityManager = entityManager;
        this.targetService = targetService;
        this.caseRepository = caseRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional
    public ReportResponse create(UUID reporterId, CreateReportRequest request) {
        lockTarget(request.targetType(), request.targetId());
        if (reportRepository.findActiveDuplicate(
                reporterId,
                request.targetType(),
                request.targetId(),
                request.reasonCode()
        ).isPresent()) {
            throw new ConflictException("An active report already exists for this target and reason");
        }

        TargetValidationStatus validationStatus = targetService.validateForReport(
                request.targetType(), request.targetId(), reporterId);
        Instant now = Instant.now();
        ModerationCase moderationCase = caseRepository.findActiveByTarget(
                        request.targetType(), request.targetId())
                .map(existing -> {
                    existing.attachReport(validationStatus, now);
                    return existing;
                })
                .orElseGet(() -> caseRepository.saveAndFlush(ModerationCase.open(
                        request.targetType(), request.targetId(), validationStatus, now)));

        Report report = reportRepository.save(Report.create(
                moderationCase.getId(),
                reporterId,
                request.targetType(),
                request.targetId(),
                request.reasonCode(),
                normalizeEvidence(request.evidenceText()),
                now
        ));
        return ReportResponse.from(report, moderationCase);
    }

    @Transactional(readOnly = true)
    public ReportHistoryResponse history(UUID reporterId,
                                         Instant beforeCreatedAt,
                                         UUID beforeId,
                                         int requestedLimit) {
        int limit = validateLimit(requestedLimit);
        if ((beforeCreatedAt == null) != (beforeId == null)) {
            throw new InvalidRequestException("Both history cursor fields are required together");
        }
        List<Report> fetched = new ArrayList<>(reportRepository.findReporterHistory(
                reporterId,
                beforeCreatedAt,
                beforeId,
                PageRequest.of(0, limit + 1)
        ));
        boolean hasMore = fetched.size() > limit;
        if (hasMore) {
            fetched.remove(fetched.size() - 1);
        }
        Map<UUID, ModerationCase> cases = caseRepository.findAllById(
                        fetched.stream().map(Report::getCaseId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ModerationCase::getId, Function.identity()));
        List<ReportResponse> content = fetched.stream()
                .map(report -> ReportResponse.history(report, cases.get(report.getCaseId())))
                .toList();
        Report last = fetched.isEmpty() ? null : fetched.get(fetched.size() - 1);
        return new ReportHistoryResponse(
                content,
                hasMore ? last.getCreatedAt() : null,
                hasMore ? last.getId() : null,
                hasMore
        );
    }

    private void lockTarget(ModerationTargetType targetType, UUID targetId) {
        String lockKey = "moderation-case:" + targetType.name() + ":" + targetId;
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(:lockKey as text), 0))")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }

    private int validateLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > MAX_HISTORY_LIMIT) {
            throw new InvalidRequestException("History limit must be between 1 and " + MAX_HISTORY_LIMIT);
        }
        return requestedLimit;
    }

    private String normalizeEvidence(String evidenceText) {
        String normalized = evidenceText == null ? "" : evidenceText.strip();
        if (normalized.isBlank() || normalized.length() > 1000) {
            throw new InvalidRequestException("Report evidence is invalid");
        }
        return normalized;
    }
}
