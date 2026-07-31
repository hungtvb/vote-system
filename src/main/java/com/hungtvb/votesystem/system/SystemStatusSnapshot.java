package com.hungtvb.votesystem.system;

import java.time.Instant;
import java.util.UUID;

public record SystemStatusSnapshot(
        SystemMode mode,
        String messageVi,
        String messageEn,
        Instant estimatedEndAt,
        Instant updatedAt,
        UUID updatedBy
) {
    static SystemStatusSnapshot from(SystemStatus status) {
        return new SystemStatusSnapshot(
                status.getMode(),
                status.getMessageVi(),
                status.getMessageEn(),
                status.getEstimatedEndAt(),
                status.getUpdatedAt(),
                status.getUpdatedBy()
        );
    }
}
