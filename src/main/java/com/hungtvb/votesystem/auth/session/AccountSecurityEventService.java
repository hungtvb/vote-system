package com.hungtvb.votesystem.auth.session;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Service
public class AccountSecurityEventService {
    private final AccountSecurityEventRepository repository;

    public AccountSecurityEventService(AccountSecurityEventRepository repository) {
        this.repository = repository;
    }

    public void recordSignIn(RefreshSession session, Instant occurredAt) {
        repository.save(AccountSecurityEvent.forSession(
                session.getUserId(), AccountSecurityEventType.SIGN_IN, session, occurredAt));
    }

    public void recordRevocations(Collection<RefreshSession> sessions, Instant occurredAt) {
        repository.saveAll(sessions.stream()
                .map(session -> AccountSecurityEvent.forSession(
                        session.getUserId(), AccountSecurityEventType.SESSION_REVOKED, session, occurredAt))
                .toList());
    }

    public void recordSuspiciousReuse(RefreshSession session, Instant occurredAt) {
        repository.save(AccountSecurityEvent.forSession(
                session.getUserId(), AccountSecurityEventType.SUSPICIOUS_TOKEN_REUSE, session, occurredAt));
    }

    public void recordEmailVerificationRequested(UUID userId, Instant occurredAt) {
        repository.save(AccountSecurityEvent.hook(
                userId, AccountSecurityEventType.EMAIL_VERIFICATION_REQUESTED, occurredAt));
    }

    public void recordAccountRecoveryRequested(UUID userId, Instant occurredAt) {
        repository.save(AccountSecurityEvent.hook(
                userId, AccountSecurityEventType.ACCOUNT_RECOVERY_REQUESTED, occurredAt));
    }
}
