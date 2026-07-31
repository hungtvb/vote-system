package com.hungtvb.votesystem.system;

import com.hungtvb.votesystem.system.dto.PublicSystemStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
    private final SystemStatusService service;

    public SystemStatusController(SystemStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public PublicSystemStatusResponse status() {
        return service.publicStatus();
    }
}
