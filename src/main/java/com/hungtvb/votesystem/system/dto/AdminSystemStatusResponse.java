package com.hungtvb.votesystem.system.dto;

import com.hungtvb.votesystem.system.SystemMode;
import com.hungtvb.votesystem.system.SystemStatusSnapshot;

import java.time.Instant;
import java.util.UUID;

public record AdminSystemStatusResponse(
        SystemMode mode,
        String messageVi,
        String messageEn,
        Instant estimatedEndAt,
        Instant updatedAt,
        UUID updatedBy
) {
    public static AdminSystemStatusResponse from(SystemStatusSnapshot snapshot) {
        return new AdminSystemStatusResponse(
                snapshot.mode(),
                snapshot.messageVi(),
                snapshot.messageEn(),
                snapshot.estimatedEndAt(),
                snapshot.updatedAt(),
                snapshot.updatedBy()
        );
    }
}
