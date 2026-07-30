package com.hungtvb.votesystem.admin.usermoderation;

import com.hungtvb.votesystem.admin.audit.AdminAuditAction;
import com.hungtvb.votesystem.admin.audit.AdminAuditEvent;
import com.hungtvb.votesystem.admin.audit.AdminAuditLogService;
import com.hungtvb.votesystem.admin.audit.AdminAuditTargetType;
import com.hungtvb.votesystem.admin.usermoderation.dto.AdminUserModerationResponse;
import com.hungtvb.votesystem.auth.session.RefreshSessionRepository;
import com.hungtvb.votesystem.auth.session.RefreshSessionService;
import com.hungtvb.votesystem.common.error.ConflictException;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import com.hungtvb.votesystem.user.AccountStatus;
import com.hungtvb.votesystem.user.AppUser;
import com.hungtvb.votesystem.user.Role;
import com.hungtvb.votesystem.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUserModerationService {
    static final Duration MAX_RESTRICTION_DURATION = Duration.ofDays(365);
    private static final String ACTIVE_ADMIN_GUARD_LOCK = "vote-system:active-admin-guard";

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshSessionService refreshSessionService;
    private final AdminAuditLogService auditLogService;

    public AdminUserModerationService(EntityManager entityManager,
                                      UserRepository userRepository,
                                      RefreshSessionRepository refreshSessionRepository,
                                      RefreshSessionService refreshSessionService,
                                      AdminAuditLogService auditLogService) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.refreshSessionService = refreshSessionService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AdminUserModerationResponse suspend(UUID actorId, UUID userId, String reason, Instant until) {
        return restrict(actorId, userId, reason, until, AccountStatus.SUSPENDED,
                AdminAuditAction.ADMIN_SUSPEND_USER);
    }

    @Transactional
    public AdminUserModerationResponse ban(UUID actorId, UUID userId, String reason, Instant until) {
        return restrict(actorId, userId, reason, until, AccountStatus.BANNED,
                AdminAuditAction.ADMIN_BAN_USER);
    }

    @Transactional
    public AdminUserModerationResponse restore(UUID actorId, UUID userId, String reason) {
        AppUser user = lockUser(userId);
        Instant now = Instant.now();
        AccountStatus previousStatus = user.effectiveAccountStatus(now);
        Instant previousUntil = user.getStatusUntil();
        try {
            user.restore(now);
        } catch (IllegalStateException exception) {
            throw new ConflictException("Account moderation state does not allow this action");
        }

        appendAudit(actorId, user, AdminAuditAction.ADMIN_RESTORE_USER, reason, Map.of(
                "previous_status", previousStatus.name(),
                "new_status", AccountStatus.ACTIVE.name(),
                "previous_until", previousUntil == null ? "permanent" : previousUntil.toString()
        ));
        return AdminUserModerationResponse.from(user, 0);
    }

    @Transactional
    public AdminUserModerationResponse revokeSessions(UUID actorId, UUID userId, String reason) {
        requireDifferentAccounts(actorId, userId);
        AppUser user = lockSessionsThenUser(userId);
        int revokedSessions = refreshSessionService.revokeAll(userId);
        if (revokedSessions == 0) {
            throw new ConflictException("User has no active refresh sessions");
        }
        appendAudit(actorId, user, AdminAuditAction.ADMIN_REVOKE_SESSIONS, reason, Map.of(
                "account_status", user.effectiveAccountStatus(Instant.now()).name(),
                "revoked_sessions", Integer.toString(revokedSessions)
        ));
        return AdminUserModerationResponse.from(user, revokedSessions);
    }

    private AdminUserModerationResponse restrict(UUID actorId,
                                                  UUID userId,
                                                  String reason,
                                                  Instant until,
                                                  AccountStatus requestedStatus,
                                                  AdminAuditAction action) {
        requireDifferentAccounts(actorId, userId);
        Instant now = Instant.now();
        validateUntil(until, now);
        AppUser user = lockSessionsThenUser(userId);
        user.normalizeExpiredRestriction(now);
        AccountStatus previousStatus = user.effectiveAccountStatus(now);

        if (user.getRole() == Role.ADMIN && user.hasActiveAccess(now)) {
            lockActiveAdminGuard();
            if (userRepository.countEffectiveActiveAdmins(now) <= 1) {
                throw new ConflictException("The last active administrator cannot be restricted");
            }
        }

        try {
            user.restrict(requestedStatus, until, now);
        } catch (IllegalStateException exception) {
            throw new ConflictException("Account moderation state does not allow this action");
        }

        int revokedSessions = refreshSessionService.revokeAll(userId);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("previous_status", previousStatus.name());
        metadata.put("new_status", requestedStatus.name());
        metadata.put("restriction_until", until == null ? "permanent" : until.toString());
        metadata.put("revoked_sessions", Integer.toString(revokedSessions));
        appendAudit(actorId, user, action, reason, metadata);
        return AdminUserModerationResponse.from(user, revokedSessions);
    }

    private AppUser lockSessionsThenUser(UUID userId) {
        refreshSessionRepository.findAllActiveByUserIdForUpdate(userId);
        return lockUser(userId);
    }

    private AppUser lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void requireDifferentAccounts(UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw new ConflictException("Administrators cannot moderate their own current account");
        }
    }

    private void validateUntil(Instant until, Instant now) {
        if (until == null) {
            return;
        }
        if (!until.isAfter(now)) {
            throw new IllegalArgumentException("Restriction expiry must be in the future");
        }
        if (until.isAfter(now.plus(MAX_RESTRICTION_DURATION))) {
            throw new IllegalArgumentException("Restriction expiry exceeds the maximum duration");
        }
    }

    private void lockActiveAdminGuard() {
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtext(cast(:lockKey as text)))")
                .setParameter("lockKey", ACTIVE_ADMIN_GUARD_LOCK)
                .getSingleResult();
    }

    private void appendAudit(UUID actorId,
                             AppUser user,
                             AdminAuditAction action,
                             String reason,
                             Map<String, String> metadata) {
        auditLogService.append(new AdminAuditEvent(
                actorId,
                action,
                AdminAuditTargetType.USER,
                user.getId().toString(),
                reason,
                metadata
        ));
    }
}
