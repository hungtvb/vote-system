package com.hungtvb.votesystem.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_status")
public class SystemStatus {
    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "singleton_id", nullable = false)
    private Short singletonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private SystemMode mode;

    @Column(name = "message_vi", length = 200)
    private String messageVi;

    @Column(name = "message_en", length = 200)
    private String messageEn;

    @Column(name = "estimated_end_at")
    private Instant estimatedEndAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SystemStatus() {
    }

    public void change(SystemMode mode,
                       String messageVi,
                       String messageEn,
                       Instant estimatedEndAt,
                       UUID actorId,
                       Instant now) {
        this.mode = mode;
        this.messageVi = messageVi;
        this.messageEn = messageEn;
        this.estimatedEndAt = estimatedEndAt;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Short getSingletonId() {
        return singletonId;
    }

    public SystemMode getMode() {
        return mode;
    }

    public String getMessageVi() {
        return messageVi;
    }

    public String getMessageEn() {
        return messageEn;
    }

    public Instant getEstimatedEndAt() {
        return estimatedEndAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }
}
