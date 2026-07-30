package com.hungtvb.votesystem.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final AdminBootstrapService service;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, AdminBootstrapService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        boolean promoted = service.promoteExistingUser(properties.email());
        log.info(promoted ? "Admin bootstrap promoted the configured account" : "Admin bootstrap target is already an administrator");
    }
}
