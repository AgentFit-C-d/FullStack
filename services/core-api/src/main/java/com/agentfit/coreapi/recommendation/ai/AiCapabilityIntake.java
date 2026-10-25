package com.agentfit.coreapi.recommendation.ai;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates AI-proposed Capability assessments before B can use them.
 * The allowed field paths must come from A's trusted snapshot contract, never from AI output.
 * A successful HTTP/LLM response and the wire schema must be checked separately by the caller.
 */
public final class AiCapabilityIntake {
    private static final Set<String> APPROVED_DIRECT_SOURCE_FIELDS = Set.of(
        "profile.projectName",
        "profile.projectType",
        "profile.domain",
        "developer.role",
        "developer.version",
        "environment.osFamily",
        "environment.clientId",
        "environment.clientVersion"
    );
    private static final Set<String> APPROVED_PROFILE_ITEM_FIELDS = Set.of(
        "database",
        "deployment",
        "frontend",
        "backend",
        "ai",
        "features",
        "external_integrations"
    );

    private AiCapabilityIntake() {}

    public enum Need { REQUIRED, OPTIONAL, UNDETERMINED, NOT_REQUESTED }

    public record Claim(String capabilityKey, Need need, String reason, List<String> sourceFields) {}

    public record Question(String field, String reason) {}

    public static final class Validated {
        private final List<Claim> claims;
        private final List<Question> questions;
        private final Set<String> requiredCapabilityKeys;
        private final boolean hasUndeterminedCapability;

        private Validated(List<Claim> claims, List<Question> questions,
                          Set<String> requiredCapabilityKeys, boolean hasUndeterminedCapability) {
            this.claims = List.copyOf(claims);
            this.questions = List.copyOf(questions);
            this.requiredCapabilityKeys = Set.copyOf(requiredCapabilityKeys);
            this.hasUndeterminedCapability = hasUndeterminedCapability;
        }

        public List<Claim> claims() { return claims; }
        public List<Question> questions() { return questions; }
        public Set<String> requiredCapabilityKeys() { return requiredCapabilityKeys; }
        public boolean hasUndeterminedCapability() { return hasUndeterminedCapability; }
    }

    public static Validated validate(List<Claim> claims, List<Question> questions,
                                     Set<String> allowedSourceFields) {
        if (claims == null || claims.isEmpty() || questions == null || allowedSourceFields == null) {
            throw invalid();
        }
        if (allowedSourceFields.stream().anyMatch(field -> blank(field) || !isApprovedSourceField(field))) {
            throw invalid();
        }
        Set<String> allowed = Set.copyOf(allowedSourceFields);
        if (claims.size() > CapabilityKey.values().length) throw invalid();

        Set<String> seenCapabilities = new HashSet<>();
        Set<String> required = new HashSet<>();
        List<Claim> safeClaims = new ArrayList<>();
        boolean undetermined = false;
        for (Claim claim : claims) {
            if (claim == null || claim.capabilityKey() == null || claim.need() == null
                || !CapabilityKey.keys().contains(claim.capabilityKey())
                || !seenCapabilities.add(claim.capabilityKey()) || blank(claim.reason())
                || claim.sourceFields() == null) {
                throw invalid();
            }
            if (claim.sourceFields().stream().anyMatch(AiCapabilityIntake::blank)) throw invalid();
            List<String> fields = List.copyOf(claim.sourceFields());
            if ((claim.need() == Need.REQUIRED || claim.need() == Need.OPTIONAL) && fields.isEmpty()) {
                throw invalid();
            }
            if (claim.need() == Need.NOT_REQUESTED && !fields.isEmpty()) {
                throw invalid();
            }
            if (new HashSet<>(fields).size() != fields.size()
                || fields.stream().anyMatch(field -> !allowed.contains(field))) {
                throw invalid();
            }
            safeClaims.add(new Claim(claim.capabilityKey(), claim.need(), claim.reason(), fields));
            if (claim.need() == Need.REQUIRED) required.add(claim.capabilityKey());
            if (claim.need() == Need.UNDETERMINED) undetermined = true;
        }

        Set<String> seenQuestionFields = new HashSet<>();
        List<Question> safeQuestions = new ArrayList<>();
        for (Question question : questions) {
            if (question == null || blank(question.field()) || blank(question.reason())
                || !allowed.contains(question.field()) || !seenQuestionFields.add(question.field())) {
                throw invalid();
            }
            safeQuestions.add(question);
        }
        return new Validated(safeClaims, safeQuestions, required, undetermined);
    }

    /**
     * AI may receive only the confirmed planning fields and the separately trusted runtime context.
     * Every profile item is checked by its field key, so legacy fields such as purpose, target_users,
     * and constraints cannot become valid merely because a snapshot lists them as allowed.
     */
    public static boolean isApprovedSourceField(String field) {
        if (blank(field)) return false;
        if (APPROVED_DIRECT_SOURCE_FIELDS.contains(field)) return true;

        if (field.startsWith("developer.responsibility.")) {
            try {
                return Integer.parseInt(field.substring("developer.responsibility.".length())) >= 0;
            } catch (NumberFormatException exception) {
                return false;
            }
        }

        String[] parts = field.split("\\.", -1);
        if (parts.length != 4 || !"profile".equals(parts[0]) || !"item".equals(parts[1])
            || !APPROVED_PROFILE_ITEM_FIELDS.contains(parts[2])) {
            return false;
        }
        try {
            return Integer.parseInt(parts[3]) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid AI Capability assessment");
    }
}
