package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserOptionalCapabilitySelectionTest {
    @Test
    void explicitSelectionPromotesOnlyNotRequestedClaimWithUserReference() {
        var claims = CapabilityKey.keys().stream().sorted().map(key ->
            new AiCapabilityIntake.Claim(key,
                key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                    : AiCapabilityIntake.Need.NOT_REQUESTED,
                "AI assessment", key.equals("cap_document_reference")
                    ? List.of("profile.item.features.0") : List.<String>of())).toList();
        var selected = UserOptionalCapabilitySelection.apply(claims,
            Set.of("cap_test_execution"));
        assertEquals(AiCapabilityIntake.Need.OPTIONAL, selected.stream()
            .filter(claim -> claim.capabilityKey().equals("cap_test_execution"))
            .findFirst().orElseThrow().need());
        assertEquals(List.of("user.optional.cap_test_execution"), selected.stream()
            .filter(claim -> claim.capabilityKey().equals("cap_test_execution"))
            .findFirst().orElseThrow().sourceFields());
        assertEquals(AiCapabilityIntake.Need.REQUIRED, selected.stream()
            .filter(claim -> claim.capabilityKey().equals("cap_document_reference"))
            .findFirst().orElseThrow().need());
    }

    @Test
    void requiredClaimCannotBeDowngradedToOptional() {
        var claims = List.of(new AiCapabilityIntake.Claim("cap_document_reference",
            AiCapabilityIntake.Need.REQUIRED, "AI assessment", List.of("profile.item.features.0")));
        assertThrows(IllegalArgumentException.class,
            () -> UserOptionalCapabilitySelection.apply(claims, Set.of("cap_document_reference")));
    }
}
