package com.agentfit.coreapi.integration.ab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AiCapabilityAnalyzerAdapterFactsTest {
    @Test
    void sendsOnlyConfirmedWorkAndResponsibilitiesToSkillNeeds() {
        var facts = AiCapabilityAnalyzerAdapter.facts(Map.of(
            "profile.projectName", "상품 리뷰 서비스",
            "profile.item.features.0", "브라우저 검증",
            "developer.responsibility.0", "테스트 실행",
            "environment.clientVersion", "2.1.270",
            "developer.version", "3"));

        assertEquals(Set.of("profile.item.features.0", "developer.responsibility.0"),
            facts.stream().map(fact -> (String) fact.get("id")).collect(Collectors.toSet()));
    }
}
