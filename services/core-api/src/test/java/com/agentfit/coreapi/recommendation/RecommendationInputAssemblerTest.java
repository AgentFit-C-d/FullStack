package com.agentfit.coreapi.recommendation;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationInputAssemblerTest {
    @Test
    void takesOnlyValidatedRequiredClaimsAndPreservesTrustedContext() {
        List<AiCapabilityIntake.Claim> claims = new ArrayList<>();
        for (String key : CapabilityKey.keys()) {
            AiCapabilityIntake.Need need = key.equals("cap_document_reference")
                ? AiCapabilityIntake.Need.REQUIRED : key.equals("cap_browser_verification")
                    ? AiCapabilityIntake.Need.UNDETERMINED : AiCapabilityIntake.Need.OPTIONAL;
            claims.add(new AiCapabilityIntake.Claim(key, need, "기획 근거", need == AiCapabilityIntake.Need.UNDETERMINED
                ? List.of() : List.of("project.purpose")));
        }
        var assessment = AiCapabilityIntake.validate(claims, List.of(), Set.of("project.purpose"));
        EnvironmentTarget target = new EnvironmentTarget("WINDOWS", "example-client", "1.0");
        Map<String, String> installed = new HashMap<>();
        installed.put("doc", null);

        RecommendationInput input = RecommendationInputAssembler.assemble(assessment, true, target, installed);
        installed.put("doc", "1");

        assertEquals(Set.of("cap_document_reference"), input.requiredCapabilityKeys());
        assertTrue(input.hasRelevantUndeterminedCapability());
        assertTrue(input.hasRelevantPendingConflict());
        assertEquals(target, input.environment());
        assertTrue(input.installedToolVersions().containsKey("doc"));
        assertNull(input.installedToolVersions().get("doc"));
    }

    @Test
    void rejectsMissingValidatedAssessmentWithoutInventingAnEmptySuccess() {
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationInputAssembler.assemble(null, false, null, null));
        var partial = AiCapabilityIntake.validate(List.of(new AiCapabilityIntake.Claim(
            "cap_document_reference", AiCapabilityIntake.Need.OPTIONAL, "근거", List.of("project.purpose"))),
            List.of(), Set.of("project.purpose"));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationInputAssembler.assemble(partial, false, null, null));
    }
}
