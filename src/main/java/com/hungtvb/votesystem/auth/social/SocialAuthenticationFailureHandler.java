package com.hungtvb.votesystem.auth.social;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SocialAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private final SocialRedirects redirects;

    public SocialAuthenticationFailureHandler(SocialRedirects redirects) {
        this.redirects = redirects;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        SocialAuthContext context = context(request);
        String code = exception instanceof OAuth2AuthenticationException oauthException
                ? oauthException.getError().getErrorCode()
                : "oauth_failed";
        clearOAuthSession(request);
        response.sendRedirect(redirects.failure(code, context.intent()));
    }

    private SocialAuthContext context(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SocialAuthContext.SESSION_ATTRIBUTE);
        return value instanceof SocialAuthContext context
                ? context
                : SocialAuthContext.authenticate(SocialIntent.AUTHENTICATE);
    }

    private void clearOAuthSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
