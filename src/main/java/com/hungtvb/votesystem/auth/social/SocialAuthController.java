package com.hungtvb.votesystem.auth.social;

import com.hungtvb.votesystem.auth.social.dto.SocialStartRequest;
import com.hungtvb.votesystem.auth.social.dto.SocialStartResponse;
import com.hungtvb.votesystem.common.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/social")
public class SocialAuthController {
    private final ClientRegistrationRepository clientRegistrationRepository;

    public SocialAuthController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @PostMapping("/{providerId}/start")
    SocialStartResponse start(@PathVariable String providerId,
                              @RequestBody(required = false) SocialStartRequest request,
                              HttpServletRequest servletRequest) {
        SocialProvider provider = enabledProvider(providerId);
        SocialIntent intent;
        try {
            intent = SocialIntent.fromWireValue(request == null ? null : request.intent());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        if (intent == SocialIntent.LINK_ACCOUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the authenticated link endpoint");
        }
        servletRequest.getSession(true)
                .setAttribute(SocialAuthContext.SESSION_ATTRIBUTE, SocialAuthContext.authenticate(intent));
        return response(provider);
    }

    @PostMapping("/{providerId}/link/start")
    SocialStartResponse startLink(@PathVariable String providerId,
                                  @AuthenticationPrincipal Jwt jwt,
                                  HttpServletRequest servletRequest) {
        SocialProvider provider = enabledProvider(providerId);
        servletRequest.getSession(true).setAttribute(
                SocialAuthContext.SESSION_ATTRIBUTE,
                SocialAuthContext.link(UUID.fromString(jwt.getSubject())));
        return response(provider);
    }

    private SocialProvider enabledProvider(String providerId) {
        SocialProvider provider = SocialProvider.fromRegistrationId(providerId);
        if (clientRegistrationRepository.findByRegistrationId(provider.registrationId()) == null) {
            throw new ResourceNotFoundException("Social login provider is not configured");
        }
        return provider;
    }

    private SocialStartResponse response(SocialProvider provider) {
        String authorizationUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/oauth2/authorization/{providerId}")
                .buildAndExpand(provider.registrationId())
                .toUriString();
        return new SocialStartResponse(authorizationUrl);
    }
}
