package com.hungtvb.votesystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class NextStaticAssetSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain nextStaticAssetSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(
                        "/_next/**",
                        "/favicon.ico",
                        "/favicon.svg",
                        "/manifest.webmanifest",
                        "/robots.txt")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.disable())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
