package com.hungtvb.votesystem.system.dto;

import com.hungtvb.votesystem.system.SystemMode;
import com.hungtvb.votesystem.system.SystemStatusSnapshot;

import java.time.Instant;

public record PublicSystemStatusResponse(
        SystemMode mode,
        String messageVi,
        String messageEn,
        Instant estimatedEndAt,
        Instant updatedAt
) {
    public static PublicSystemStatusResponse from(SystemStatusSnapshot snapshot) {
        return new PublicSystemStatusResponse(
                snapshot.mode(),
                snapshot.messageVi(),
                snapshot.messageEn(),
                snapshot.estimatedEndAt(),
                snapshot.updatedAt()
        );
    }
}
