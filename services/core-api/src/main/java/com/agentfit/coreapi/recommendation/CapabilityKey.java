package com.agentfit.coreapi.recommendation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Capability IDs agreed in the first A/B integration baseline. */
public enum CapabilityKey {
    CAP_DOCUMENT_REFERENCE("cap_document_reference"),
    CAP_REPOSITORY_NAVIGATION("cap_repository_navigation"),
    CAP_CODE_REVIEW("cap_code_review"),
    CAP_TEST_AUTHORING("cap_test_authoring"),
    CAP_TEST_EXECUTION("cap_test_execution"),
    CAP_BROWSER_VERIFICATION("cap_browser_verification"),
    CAP_API_VERIFICATION("cap_api_verification"),
    CAP_DATABASE_SCHEMA_INSPECTION("cap_database_schema_inspection"),
    CAP_AI_EVALUATION("cap_ai_evaluation");

    private final String key;

    CapabilityKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Set<String> keys() {
        return Arrays.stream(values()).map(CapabilityKey::key).collect(Collectors.toUnmodifiableSet());
    }
}
