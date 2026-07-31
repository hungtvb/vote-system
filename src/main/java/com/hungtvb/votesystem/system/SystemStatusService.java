package com.hungtvb.votesystem.system;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.InvalidRequestException;
import com.hungtvb.votesystem.system.dto.AdminSystemStatusResponse;
import com.hungtvb.votesystem.system.dto.PublicSystemStatusResponse;
import com.hungtvb.votesystem.system.dto.UpdateSystemStatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SystemStatusService {
    private static final short STATUS_ID = SystemStatus.SINGLETON_ID;

    private final SystemStatusRepository repository;
    private final SystemStatusCache cache;
    private final AdminAuditLogService auditLogService;

    public SystemStatusService(SystemStatusRepository repository,
                               SystemStatusCache cache,
                               AdminAuditLogService auditLogService) {
        this.repository = repository;
        this.cache = cache;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PublicSystemStatusResponse publicStatus() {
        return PublicSystemStatusResponse.from(currentSnapshot());
    }

    @Transactional(readOnly = true)
    public AdminSystemStatusResponse adminStatus() {
        return AdminSystemStatusResponse.from(currentSnapshot());
    }

    @Transactional
    public AdminSystemStatusResponse update(UUID actorId,
                                            UpdateSystemStatusRequest request,
                                            String requestId) {
        Objects.requireNonNull(actorId, "System status actor is required");
        Objects.requireNonNull(request, "System status request is required");
        cache.evict();

        SystemStatus status = repository.findSingletonForUpdate()
                .orElseThrow(() -> new IllegalStateException("System status row is missing"));
        Instant now = Instant.now();
        NormalizedUpdate update = normalize(request, now);
        SystemStatusSnapshot previous = SystemStatusSnapshot.from(status);
        if (sameState(previous, update)) {
            throw new ConflictException("System status is unchanged");
        }

        status.change(
                update.mode(),
                update.messageVi(),
                update.messageEn(),
                update.estimatedEndAt(),
                actorId,
                now
        );
        repository.saveAndFlush(status);
        SystemStatusSnapshot changed = SystemStatusSnapshot.from(status);

        auditLogService.append(new AdminAuditEvent(
                actorId,
                AdminAuditAction.SYSTEM_MODE_CHANGED,
                AdminAuditTargetType.SYSTEM,
                "GLOBAL",
                request.reason().strip(),
                auditMetadata(previous, changed, requestId)
        ));
        publishCacheAfterCommit(changed);
        return AdminSystemStatusResponse.from(changed);
    }

    public void evictCache() {
        cache.evict();
    }

    @Transactional(readOnly = true)
    public SystemStatusSnapshot currentStatusSnapshot() {
        return currentSnapshot();
    }

    private SystemStatusSnapshot currentSnapshot() {
        return cache.get(() -> repository.findById(STATUS_ID)
                .map(SystemStatusSnapshot::from)
                .orElseThrow(() -> new IllegalStateException("System status row is missing")));
    }

    private NormalizedUpdate normalize(UpdateSystemStatusRequest request, Instant now) {
        SystemMode mode = Objects.requireNonNull(request.mode(), "System mode is required");
        String messageVi = normalizeMessage(request.messageVi());
        String messageEn = normalizeMessage(request.messageEn());
        Instant estimatedEndAt = request.estimatedEndAt();

        if (mode == SystemMode.NORMAL) {
            messageVi = null;
            messageEn = null;
            estimatedEndAt = null;
        } else if (estimatedEndAt != null && !estimatedEndAt.isAfter(now)) {
            throw new InvalidRequestException("estimatedEndAt must be in the future");
        }

        return new NormalizedUpdate(mode, messageVi, messageEn, estimatedEndAt);
    }

    private String normalizeMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > 200) {
            throw new InvalidRequestException("System status message is too long");
        }
        return value;
    }

    private boolean sameState(SystemStatusSnapshot current, NormalizedUpdate update) {
        return current.mode() == update.mode()
                && Objects.equals(current.messageVi(), update.messageVi())
                && Objects.equals(current.messageEn(), update.messageEn())
                && Objects.equals(current.estimatedEndAt(), update.estimatedEndAt());
    }

    private Map<String, String> auditMetadata(SystemStatusSnapshot previous,
                                              SystemStatusSnapshot changed,
                                              String requestId) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("previous_mode", previous.mode().name());
        metadata.put("new_mode", changed.mode().name());
        metadata.put("message_vi", auditValue(changed.messageVi()));
        metadata.put("message_en", auditValue(changed.messageEn()));
        metadata.put("estimated_end_at", changed.estimatedEndAt() == null
                ? "none"
                : changed.estimatedEndAt().toString());
        metadata.put("request_id", safeRequestId(requestId));
        return metadata;
    }

    private String auditValue(String value) {
        return value == null ? "none" : value;
    }

    private String safeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "missing";
        }
        String value = requestId.strip();
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private void publishCacheAfterCommit(SystemStatusSnapshot snapshot) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.put(snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.put(snapshot);
            }
        });
    }

    private record NormalizedUpdate(
            SystemMode mode,
            String messageVi,
            String messageEn,
            Instant estimatedEndAt
    ) {
    }
}
