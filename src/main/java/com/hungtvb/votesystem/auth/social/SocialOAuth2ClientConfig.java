package com.hungtvb.votesystem.auth.social;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(SocialLoginProperties.class)
public class SocialOAuth2ClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(SocialLoginProperties properties) {
        Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
        addGoogle(registrations, properties.google());
        addGithub(registrations, properties.github());
        return new ConfiguredClientRegistrationRepository(registrations);
    }

    private void addGoogle(Map<String, ClientRegistration> registrations,
                           SocialLoginProperties.ProviderCredentials credentials) {
        if (!credentials.enabled()) {
            return;
        }
        ClientRegistration registration = CommonOAuth2Provider.GOOGLE
                .getBuilder(SocialProvider.GOOGLE.registrationId())
                .clientId(credentials.clientId())
                .clientSecret(credentials.clientSecret())
                .scope("openid", "profile", "email")
                .build();
        registrations.put(registration.getRegistrationId(), registration);
    }

    private void addGithub(Map<String, ClientRegistration> registrations,
                           SocialLoginProperties.ProviderCredentials credentials) {
        if (!credentials.enabled()) {
            return;
        }
        ClientRegistration registration = CommonOAuth2Provider.GITHUB
                .getBuilder(SocialProvider.GITHUB.registrationId())
                .clientId(credentials.clientId())
                .clientSecret(credentials.clientSecret())
                .scope("read:user")
                .build();
        registrations.put(registration.getRegistrationId(), registration);
    }

    static final class ConfiguredClientRegistrationRepository
            implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        private final Map<String, ClientRegistration> registrations;

        private ConfiguredClientRegistrationRepository(Map<String, ClientRegistration> registrations) {
            this.registrations = Map.copyOf(registrations);
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return registrations.get(registrationId);
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return registrations.values().iterator();
        }
    }
}
