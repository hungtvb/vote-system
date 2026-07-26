package com.hungtvb.votesystem.auth.social;

public class SocialLoginException extends RuntimeException {
    private final String code;

    public SocialLoginException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
