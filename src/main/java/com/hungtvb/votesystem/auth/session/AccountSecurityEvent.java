package com.hungtvb.votesystem.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_security_events")
public class AccountSecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AccountSecurityEventType eventType;

    @Column(name = "session_family_id")
    private UUID sessionFamilyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16)
    private SessionProvider provider;

    @Column(name = "client_label", length = 64)
    private String clientLabel;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AccountSecurityEvent() {
    }

    private AccountSecurityEvent(UUID userId,
                                 AccountSecurityEventType eventType,
                                 UUID sessionFamilyId,
                                 SessionProvider provider,
                                 String clientLabel,
                                 Instant occurredAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.sessionFamilyId = sessionFamilyId;
        this.provider = provider;
        this.clientLabel = clientLabel;
        this.occurredAt = occurredAt;
    }

    public static AccountSecurityEvent forSession(UUID userId,
                                                  AccountSecurityEventType eventType,
                                                  RefreshSession session,
                                                  Instant occurredAt) {
        return new AccountSecurityEvent(
                userId,
                eventType,
                session.getFamilyId(),
                session.getProvider(),
                session.getClientLabel(),
                occurredAt
        );
    }

    public static AccountSecurityEvent hook(UUID userId,
                                            AccountSecurityEventType eventType,
                                            Instant occurredAt) {
        return new AccountSecurityEvent(userId, eventType, null, null, null, occurredAt);
    }
}
