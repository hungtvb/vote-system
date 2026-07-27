package com.hungtvb.votesystem.auth.session;

import com.hungtvb.votesystem.common.error.UnauthorizedException;

public class RefreshSessionFailureException extends UnauthorizedException {
    private final Reason reason;

    public RefreshSessionFailureException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        NO_COOKIE("no_cookie"),
        INVALID("invalid"),
        EXPIRED("expired"),
        REVOKED("revoked"),
        REUSED("reused");

        private final String metricValue;

        Reason(String metricValue) {
            this.metricValue = metricValue;
        }

        public String metricValue() {
            return metricValue;
        }
    }
}
