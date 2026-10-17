package com.agentfit.coreapi.recommendation.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiCapabilityIntakeTest {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
        "profile.projectName", "profile.item.features.0", "environment.clientId");

    private AiCapabilityIntake.Claim claim(String key, AiCapabilityIntake.Need need, List<String> fields) {
        return new AiCapabilityIntake.Claim(key, need, "문서의 요구", fields);
    }

    @Test
    void rejectsEmptyAssessmentEvenWhenItContainsOnlyQuestions() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(new AiCapabilityIntake.Question("environment.clientId", "확인 필요")), ALLOWED_FIELDS));
    }

    @Test
    void preservesOnlyValidatedRequiredOptionalAndUndeterminedAssessments() {
        var result = AiCapabilityIntake.validate(List.of(
            claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of("profile.item.features.0")),
            claim("cap_code_review", AiCapabilityIntake.Need.OPTIONAL, List.of("profile.projectName")),
            claim("cap_browser_verification", AiCapabilityIntake.Need.UNDETERMINED, List.of())),
            List.of(new AiCapabilityIntake.Question("environment.clientId", "클라이언트 확인 필요")), ALLOWED_FIELDS);

        assertEquals(Set.of("cap_document_reference"), result.requiredCapabilityKeys());
        assertTrue(result.hasUndeterminedCapability());
        assertEquals(3, result.claims().size());
        assertEquals(1, result.questions().size());
    }

    @Test
    void rejectsUnknownOrDuplicateCapabilityIds() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_invented", AiCapabilityIntake.Need.REQUIRED, List.of("profile.item.features.0"))),
            List.of(), ALLOWED_FIELDS));
        var same = claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of("profile.item.features.0"));
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
    void rejectsExcludedPlanningFieldsEvenWhenTheyAreClaimedAsAllowed() {
        Set<String> forbidden = Set.of("profile.item.purpose.0");

        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                List.of("profile.item.purpose.0"))), List.of(), forbidden));
    }

    @Test
    void rejectsIncompleteClaimsAndFreezesValidatedOutput() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, List.of())),
            List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(new AiCapabilityIntake.Claim("cap_document_reference", null, "근거", List.of("profile.item.features.0"))),
            List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(new AiCapabilityIntake.Claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                " ", List.of("profile.item.features.0"))), List.of(), ALLOWED_FIELDS));

        List<String> fields = new ArrayList<>(List.of("profile.item.features.0"));
        List<AiCapabilityIntake.Claim> claims = new ArrayList<>(List.of(
            claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED, fields)));
        var result = AiCapabilityIntake.validate(claims, List.of(), ALLOWED_FIELDS);
        fields.add("environment.clientId");
        claims.clear();
        assertEquals(List.of("profile.item.features.0"), result.claims().getFirst().sourceFields());
        assertThrows(UnsupportedOperationException.class, () -> result.claims().clear());
    }

    @Test
    void rejectsNullAndRepeatedFieldPathsWithOneSafeErrorType() {
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                Arrays.asList("profile.item.features.0", null))), List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(claim("cap_document_reference", AiCapabilityIntake.Need.REQUIRED,
                List.of("profile.item.features.0", "profile.item.features.0"))), List.of(), ALLOWED_FIELDS));
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(new AiCapabilityIntake.Question("profile.projectName", "확인"),
                new AiCapabilityIntake.Question("profile.projectName", "재확인")), ALLOWED_FIELDS));
        Set<String> invalidAllowed = new HashSet<>(ALLOWED_FIELDS);
        invalidAllowed.add(null);
        assertThrows(IllegalArgumentException.class, () -> AiCapabilityIntake.validate(
            List.of(), List.of(), invalidAllowed));
    }
}
