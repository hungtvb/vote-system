package com.hungtvb.votesystem.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminProbeController {

    @GetMapping("/probe")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminProbeResponse probe() {
        return new AdminProbeResponse("ok");
    }

    public record AdminProbeResponse(String status) {
    }
}
