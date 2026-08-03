package com.hungtvb.votesystem.auth.session;

public enum AccountSecurityEventType {
    SIGN_IN,
    SESSION_REVOKED,
    SUSPICIOUS_TOKEN_REUSE,
    EMAIL_VERIFICATION_REQUESTED,
    ACCOUNT_RECOVERY_REQUESTED
}
