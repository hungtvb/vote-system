package com.hungtvb.votesystem.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hungtvb.votesystem.admin.audit.dto.AdminAuditLogResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTests {

    @Mock AdminAuditLogStore store;
    @Mock AdminAuditLogRepository repository;

    @Test
    void appendsNormalizedSafeAuditEvent() {
        AdminAuditLogService service = service();
        UUID actorId = UUID.randomUUID();
        when(store.append(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminAuditLogResponse response = service.append(new AdminAuditEvent(
                actorId,
                AdminAuditAction.ADMIN_HIDE_POST,
                AdminAuditTargetType.POST,
                "  " + UUID.randomUUID() + "  ",
                "  Violates published rules  ",
                Map.of("request_id", "  req-123  ")
        ));

        assertEquals(actorId, response.actorId());
        assertEquals(AdminAuditAction.ADMIN_HIDE_POST, response.action());
        assertEquals(AdminAuditTargetType.POST, response.targetType());
        assertEquals("Violates published rules", response.reason());
        assertEquals(Map.of("request_id", "req-123"), response.metadata());
    }

    @Test
    void rejectsMissingRequiredFieldsBeforePersistence() {
        AdminAuditLogService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.append(null));
        assertThrows(IllegalArgumentException.class, () -> service.append(new AdminAuditEvent(
                null,
                AdminAuditAction.ADMIN_BAN_USER,
                AdminAuditTargetType.USER,
                UUID.randomUUID().toString(),
                "Required moderation action",
                Map.of()
        )));
        assertThrows(IllegalArgumentException.class, () -> service.append(new AdminAuditEvent(
                UUID.randomUUID(),
                AdminAuditAction.ADMIN_BAN_USER,
                AdminAuditTargetType.USER,
                UUID.randomUUID().toString(),
                "   ",
                Map.of()
        )));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsIncompatibleActionAndTargetType() {
        AdminAuditLogService service = service();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.append(
                new AdminAuditEvent(
                        UUID.randomUUID(),
                        AdminAuditAction.ADMIN_BAN_USER,
                        AdminAuditTargetType.POST,
                        UUID.randomUUID().toString(),
                        "Invalid target combination",
                        Map.of()
                )
        ));

        assertEquals("Audit action and target type are incompatible", error.getMessage());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsSensitiveOrUnsafeMetadata() {
        AdminAuditLogService service = service();
        AdminAuditEvent base = event(Map.of());

        assertThrows(IllegalArgumentException.class, () -> service.append(new AdminAuditEvent(
                base.actorId(), base.action(), base.targetType(), base.targetId(), base.reason(),
                Map.of("access_token", "redacted")
        )));
        assertThrows(IllegalArgumentException.class, () -> service.append(new AdminAuditEvent(
                base.actorId(), base.action(), base.targetType(), base.targetId(), base.reason(),
                Map.of("request_id", "person@example.com")
        )));
        assertThrows(IllegalArgumentException.class, () -> service.append(new AdminAuditEvent(
                base.actorId(), base.action(), base.targetType(), base.targetId(), base.reason(),
                Map.of("RequestId", "req-123")
        )));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsOversizedMetadata() {
        AdminAuditLogService service = service();
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            metadata.put("field_" + index, "x".repeat(200));
        }

        AdminAuditEvent base = event(metadata);
        assertThrows(IllegalArgumentException.class, () -> service.append(base));
        verifyNoInteractions(store);
    }

    private AdminAuditLogService service() {
        return new AdminAuditLogService(store, repository, new ObjectMapper());
    }

    private AdminAuditEvent event(Map<String, String> metadata) {
        return new AdminAuditEvent(
                UUID.randomUUID(),
                AdminAuditAction.ADMIN_REBUILD_RANKING,
                AdminAuditTargetType.RANKING,
                "ALL",
                "Operational rebuild requested",
                metadata
        );
    }
}
