package com.agentfit.coreapi.recommendation.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiCapabilityIntakeTest {
    private static final Set<String> ALLOWED_FIELDS = Set.of("project.purpose", "developer.role", "environment.client");

    private AiCapabilityIntake.Claim claim(String key, AiCapabilityIntake.Need need, List<String> fields) {
        return new AiCapabilityIntake.Claim(key, need, "문서의 요구", fields);
    }

    @Test
    void rejectsEmptyAssessmentEvenWhenItContainsOnlyQuestions() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(new AiCapabilityIntake.Question("environment.client", "확인 필요")), ALLOWED_FIELDS));
    }

    @Test
    void preservesOnlyValidatedRequiredOptionalAndUndeterminedAssessments() {
        var result = AiCapabilityIntake.validate(List.of(
            claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of("project.purpose")),
            claim("cap_code_review", AiCapabilityIntake.Need.OPTIONAL, List.of("developer.role")),
            claim("cap_browser_verification", AiCapabilityIntake.Need.UNDETERMINED, List.of())),
            List.of(new AiCapabilityIntake.Question("environment.client", "클라이언트 확인 필요")), ALLOWED_FIELDS);

        assertEquals(Set.of("cap_document_reference"), result.requiredCapabilityKeys());
        assertTrue(result.hasUndeterminedCapability());
        assertEquals(3, result.claims().size());
        assertEquals(1, result.questions().size());
    }

    @Test
    void rejectsUnknownOrDuplicateCapabilityIds() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_invented", AiCapabilityIntake.Need.REQUIRED, List.of("project.purpose"))),
            List.of(), ALLOWED_FIELDS));
        var same = claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of("project.purpose"));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(same, same), List.of(), ALLOWED_FIELDS));
    }

    @Test
    void rejectsExcludedSourceAndQuestionFields() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of("project.rawDocument"))),
            List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(new AiCapabilityIntake.Question("environment.secret", "필요")), ALLOWED_FIELDS));
    }

    @Test
    void rejectsIncompleteClaimsAndFreezesValidatedOutput() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of())),
            List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(new AiCapabilityIntake.Claim("cap_document_reference", null, "근거", List.of("project.purpose"))),
            List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(new AiCapabilityIntake.Claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                " ", List.of("project.purpose"))), List.of(), ALLOWED_FIELDS));

        List<String> fields = new ArrayList<>(List.of("project.purpose"));
        List<AiCapabilityIntake.Claim> claims = new ArrayList<>(List.of(
            claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, fields)));
        var result = AiCapabilityIntake.validate(claims, List.of(), ALLOWED_FIELDS);
        fields.add("environment.client");
        claims.clear();
        assertEquals(List.of("project.purpose"), result.claims().getFirst().sourceFields());
        assertThrows(UnsupportedOperationException.class, () -> result.claims().clear());
    }

    @Test
    void rejectsNullAndRepeatedFieldPathsWithOneSafeErrorType() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                Arrays.asList("project.purpose", null))), List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                List.of("project.purpose", "project.purpose"))), List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(new AiCapabilityIntake.Question("developer.role", "확인"),
                new AiCapabilityIntake.Question("developer.role", "재확인")), ALLOWED_FIELDS));
        Set<String> invalidAllowed = new HashSet<>(ALLOWED_FIELDS);
        invalidAllowed.add(null);
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(), invalidAllowed));
    }
}
