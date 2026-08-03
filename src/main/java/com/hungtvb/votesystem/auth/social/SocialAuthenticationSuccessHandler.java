package com.hungtvb.votesystem.auth.social;

import com.hungtvb.votesystem.auth.AuthService;
import com.hungtvb.votesystem.auth.IssuedAuthSession;
import com.hungtvb.votesystem.auth.RefreshTokenCookie;
import com.hungtvb.votesystem.auth.session.SessionClientMetadataFactory;
import com.hungtvb.votesystem.common.error.UnauthorizedException;
import com.hungtvb.votesystem.user.AppUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SocialAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final SocialRedirects redirects;
    private final SessionClientMetadataFactory sessionMetadataFactory;

    public SocialAuthenticationSuccessHandler(SocialLoginService socialLoginService,
                                               AuthService authService,
                                               RefreshTokenCookie refreshTokenCookie,
                                               SocialRedirects redirects,
                                               SessionClientMetadataFactory sessionMetadataFactory) {
        this.socialLoginService = socialLoginService;
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
        this.redirects = redirects;
        this.sessionMetadataFactory = sessionMetadataFactory;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            clearOAuthSession(request);
            response.sendRedirect(redirects.failure("invalid_oauth_authentication", SocialIntent.AUTHENTICATE));
            return;
        }

        SocialProvider provider = SocialProvider.fromRegistrationId(
                oauthToken.getAuthorizedClientRegistrationId());
        SocialAuthContext context = context(request);

        try {
            SocialProfile profile = SocialProfileFactory.from(provider, oauthToken.getPrincipal());
            AppUser user = socialLoginService.complete(profile, context);
            IssuedAuthSession session = authService.issueSession(
                    user, sessionMetadataFactory.social(provider, request));
            refreshTokenCookie.write(response, session.refreshToken());
            clearOAuthSession(request);
            response.sendRedirect(redirects.success(provider, context.intent()));
        } catch (UnauthorizedException exception) {
            clearOAuthSession(request);
            response.sendRedirect(redirects.failure("account_unavailable", context.intent()));
        } catch (SocialLoginException exception) {
            clearOAuthSession(request);
            response.sendRedirect(redirects.failure(exception.code(), context.intent()));
        } catch (IllegalArgumentException exception) {
            clearOAuthSession(request);
            response.sendRedirect(redirects.failure("invalid_provider_profile", context.intent()));
        }
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
