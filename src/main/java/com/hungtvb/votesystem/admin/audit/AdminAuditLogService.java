package com.hungtvb.votesystem.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.audit.dto.AdminAuditLogPageResponse;
import com.hungtvb.votesystem.admin.audit.dto.AdminAuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AdminAuditLogService {
    private static final int MAX_TARGET_ID_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_METADATA_ENTRIES = 20;
    private static final int MAX_METADATA_KEY_LENGTH = 50;
    private static final int MAX_METADATA_VALUE_LENGTH = 200;
    private static final int MAX_METADATA_BYTES = 3072;
    private static final Pattern METADATA_KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,49}");
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "secret", "token", "authorization", "cookie", "credential", "email"
    );
    private static final Set<String> PUBLIC_MESSAGE_KEYS = Set.of("message_vi", "message_en");

    private final AdminAuditLogStore store;
    private final AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AdminAuditLogService(AdminAuditLogStore store,
                                AdminAuditLogRepository repository,
                                ObjectMapper objectMapper) {
        this.store = store;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminAuditLogResponse append(AdminAuditEvent event) {
        if (event == null) {
            throw invalid("Audit event is required");
        }
        UUID actorId = required(event.actorId(), "Audit actor is required");
        AdminAuditAction action = required(event.action(), "Audit action is required");
        AdminAuditTargetType targetType = required(event.targetType(), "Audit target type is required");
        if (action.targetType() != targetType) {
            throw invalid("Audit action and target type are incompatible");
        }
        String targetId = boundedRequired(event.targetId(), MAX_TARGET_ID_LENGTH, "Audit target is invalid");
        String reason = boundedRequired(event.reason(), MAX_REASON_LENGTH, "Audit reason is invalid");
        Map<String, String> metadata = normalizeMetadata(event.metadata());

        AdminAuditLog auditLog = AdminAuditLog.create(
                actorId,
                action,
                targetType,
                targetId,
                reason,
                metadata,
                Instant.now()
        );
        return AdminAuditLogResponse.from(store.append(auditLog));
    }

    @Transactional(readOnly = true)
    public AdminAuditLogPageResponse list(Pageable pageable, AdminAuditLogFilter filter) {
        Pageable stablePage = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        AdminAuditLogFilter normalizedFilter = new AdminAuditLogFilter(
                filter.action(),
                filter.actorId(),
                filter.targetType(),
                filter.targetId() == null ? null : filter.targetId().strip()
        );
        Page<AdminAuditLog> result = repository.findAllFiltered(
                normalizedFilter.action(),
                normalizedFilter.actorId(),
                normalizedFilter.targetType(),
                normalizedFilter.targetId(),
                stablePage
        );
        return AdminAuditLogPageResponse.from(result);
    }

    private Map<String, String> normalizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            throw invalid("Audit metadata contains too many entries");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = normalizeMetadataKey(entry.getKey());
            String value = normalizeMetadataValue(key, entry.getValue());
            if (normalized.putIfAbsent(key, value) != null) {
                throw invalid("Audit metadata contains duplicate keys");
            }
        }

        try {
            if (objectMapper.writeValueAsBytes(normalized).length > MAX_METADATA_BYTES) {
                throw invalid("Audit metadata is too large");
            }
        } catch (JsonProcessingException exception) {
            throw invalid("Audit metadata is invalid");
        }
        return Collections.unmodifiableMap(normalized);
    }

    private String normalizeMetadataKey(String rawKey) {
        if (rawKey == null) {
            throw invalid("Audit metadata key is invalid");
        }
        String key = rawKey.strip();
        if (key.length() > MAX_METADATA_KEY_LENGTH || !METADATA_KEY.matcher(key).matches()) {
            throw invalid("Audit metadata key is invalid");
        }
        String lowered = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEY_PARTS.stream().anyMatch(lowered::contains)) {
            throw invalid("Audit metadata key is not allowed");
        }
        return key;
    }

    private String normalizeMetadataValue(String key, String rawValue) {
        if (rawValue == null) {
            throw invalid("Audit metadata value is invalid");
        }
        String value = rawValue.strip();
        boolean publicStatusMessage = PUBLIC_MESSAGE_KEYS.contains(key);
        if (value.length() > MAX_METADATA_VALUE_LENGTH
                || value.chars().anyMatch(Character::isISOControl)
                || (!publicStatusMessage && value.contains("@"))
                || value.toLowerCase(Locale.ROOT).contains("bearer ")) {
            throw invalid("Audit metadata value is invalid");
        }
        return value;
    }

    private String boundedRequired(String rawValue, int maxLength, String message) {
        if (rawValue == null) {
            throw invalid(message);
        }
        String value = rawValue.strip();
        if (value.isBlank() || value.length() > maxLength) {
            throw invalid(message);
        }
        return value;
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw invalid(message);
        }
        return value;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
