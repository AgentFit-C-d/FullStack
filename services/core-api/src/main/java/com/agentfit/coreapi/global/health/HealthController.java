package com.agentfit.coreapi.global.health;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System", description = "Service health")
class HealthController {
    @GetMapping("/health")
    HealthResponse health() {
        return new HealthResponse("core-api", "ok");
    }
}



