package com.agentfit.coreapi.contract;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectAnalysisContractTest {
    @Test void openApiExposesSafeProjectAndAnalysisContracts() throws Exception {
        Path path = Paths.get(System.getProperty("user.dir"));
        while (path != null && !Files.exists(path.resolve("contracts/openapi/core-api.yml"))) path = path.getParent();
        assertNotNull(path, "repository root with OpenAPI contract must be discoverable");
        String yaml = Files.readString(path.resolve("contracts/openapi/core-api.yml"));
        assertTrue(yaml.contains("version: 0.2.0"));
        assertTrue(yaml.contains("/api/projects/{projectId}/analysis-attempts:"));
        assertTrue(yaml.contains("Idempotency-Key"));
        assertTrue(yaml.contains("ANALYSIS_ALREADY_PROCESSING"));
        assertTrue(yaml.contains("ANALYSIS_USER_LIMIT_REACHED"));
        assertTrue(yaml.contains("ANALYSIS_SERVER_LIMIT_REACHED"));
        assertFalse(yaml.contains("rawContent"));
        assertFalse(yaml.contains("ownerId"));
        assertEquals(1, count(yaml, "    IdempotencyKey:"));
        assertFalse(yaml.contains("Path=/api` `AGENTFIT_SESSION"));
    }

    private static int count(String value, String token) {
        int count = 0, index = 0;
        while ((index = value.indexOf(token, index)) >= 0) { count++; index += token.length(); }
        return count;
    }
}
