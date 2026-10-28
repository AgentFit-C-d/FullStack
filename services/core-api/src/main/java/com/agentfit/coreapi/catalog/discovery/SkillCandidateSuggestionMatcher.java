package com.agentfit.coreapi.catalog.discovery;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/** Name-based discovery hints. These are never Catalog support or combination evidence. */
public final class SkillCandidateSuggestionMatcher {
    private static final Map<String, Pattern> NAME_RULES = rules();

    public record Item(String externalId, String name, String sourceUrl, String contentHash,
                       Set<String> relatedCapabilityKeys, String evidence) {
        public Item { relatedCapabilityKeys = Set.copyOf(relatedCapabilityKeys); }
    }

    public record Result(List<Item> items, List<String> proposedCombinationExternalIds,
                         Set<String> unmatchedRequiredCapabilityKeys,
                         String combinationStatus) {
        public Result {
            items = List.copyOf(items);
            proposedCombinationExternalIds = List.copyOf(proposedCombinationExternalIds);
            unmatchedRequiredCapabilityKeys = Set.copyOf(unmatchedRequiredCapabilityKeys);
        }
    }

    private SkillCandidateSuggestionMatcher() {}

    public static Result suggest(List<SkillCandidate> candidates, Set<String> required,
                                 Set<String> optional) {
        if (candidates == null || required == null || optional == null
            || !CapabilityKey.keys().containsAll(required)
            || !CapabilityKey.keys().containsAll(optional)) {
            throw new IllegalArgumentException("valid candidate list and Capability keys required");
        }
        Set<String> needed = new HashSet<>(required);
        needed.addAll(optional);
        List<Item> matches = new ArrayList<>();
        for (SkillCandidate candidate : candidates) {
            if (candidate == null) throw new IllegalArgumentException("null candidate");
            String name = candidate.name().toLowerCase(Locale.ROOT);
            Set<String> related = new HashSet<>();
            for (String key : needed) {
                if (NAME_RULES.get(key).matcher(name).find()) related.add(key);
            }
            if (!related.isEmpty()) {
                matches.add(new Item(candidate.externalId(), candidate.name(),
                    candidate.sourceUrl(), candidate.contentHash(), related, "NAME_RULE"));
            }
        }
        matches.sort(Comparator
            .comparingLong((Item item) -> item.relatedCapabilityKeys().stream()
                .filter(required::contains).count()).reversed()
            .thenComparingLong(item -> -item.relatedCapabilityKeys().stream()
                .filter(optional::contains).count())
            .thenComparing(Item::externalId));
        if (matches.size() > 20) matches = new ArrayList<>(matches.subList(0, 20));
        Set<String> unmatched = new HashSet<>(required);
        List<String> proposal = new ArrayList<>();
        for (Item item : matches) {
            if (item.relatedCapabilityKeys().stream().noneMatch(unmatched::contains)) continue;
            proposal.add(item.externalId());
            unmatched.removeAll(item.relatedCapabilityKeys());
            if (unmatched.isEmpty()) break;
        }
        return new Result(matches, proposal, unmatched, "UNCONFIRMED");
    }

    private static Map<String, Pattern> rules() {
        Map<String, Pattern> rules = new LinkedHashMap<>();
        rules.put("cap_document_reference", Pattern.compile("document|docs|reference"));
        rules.put("cap_repository_navigation", Pattern.compile("repo|code-search|navigation"));
        rules.put("cap_code_review", Pattern.compile("code-review|review-pr|pull-request-review"));
        rules.put("cap_test_authoring", Pattern.compile("test-driven|test-author|write-tests|generate-tests"));
        rules.put("cap_test_execution", Pattern.compile("run-tests|test-suite|pytest|scoutqa|momentic-test|terraform-test"));
        rules.put("cap_browser_verification", Pattern.compile("browser|playwright|e2e"));
        rules.put("cap_api_verification", Pattern.compile("api-test|http-test|integration-test"));
        rules.put("cap_database_schema_inspection", Pattern.compile("database|schema|sql"));
        rules.put("cap_ai_evaluation", Pattern.compile("ai-eval|evaluation|benchmark"));
        return Map.copyOf(rules);
    }
}
