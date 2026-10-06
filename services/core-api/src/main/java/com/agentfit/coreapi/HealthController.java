package com.agentfit.coreapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    @GetMapping("/health")
    HealthResponse health() {
        return new HealthResponse("core-api", "ok");
    }
}
