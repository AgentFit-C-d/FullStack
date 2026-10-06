# Target-specific Catalog combinations implementation plan

> **For agentic workers:** Use Superpowers test-driven-development and verification-before-completion for each task. Work in the user-selected shared `FullStack` checkout without changing A or AI ownership.

**Goal:** Allow a multi-tool recommendation or configuration choice only when that exact tool set has reviewed evidence for the user's OS, Client, and Client version.

**Architecture:** Replace the unscoped `Set<Set<String>>` with an immutable combination value containing the tool set and `EnvironmentTarget`. Parse combination entries from the verified Catalog bundle, preserving source evidence. Both recommendation and selection validation compare the entire value; a combination verified on one target never applies to another.

**Tech Stack:** Java 21 target, JUnit 5, Jackson already provided by Spring Boot.

**Spec:** `Docs/AgentFit_AB_1차통합_개발기준.pdf` sections 7–12, `Docs/fullstack-b/catalog/catalog-semantic-parser-plan.md`, and `Docs/fullstack-b/recommendation/configuration-selection-plan.md`. The JSON shape below remains a team-review proposal, not a claim that actual Codex tools have been verified.

## Global constraints

- Keep file hash verification before JSON parsing.
- Preserve exact support matching on OS family, Client ID, and Client version.
- Reject unknown tool references, duplicate combination-target pairs, incomplete targets, and invalid review evidence as Catalog errors.
- A combination record is eligible only if every member has verified standalone support on the same target, dependencies are present, and no conflict or component overlap exists.
- The `verifiedCombinations` JSON item has exactly `toolKeys`, `osFamily`, `clientId`, `clientVersion`, `evidenceUrl`, and `checkedAt`.
- Do not add a real Catalog release or public endpoint in this task.

## Review focus

- A Windows/Codex 1.0 combination must not become eligible on Codex 2.0 or macOS.
- Two different tool sets on one target remain distinct.
- A `NOT_RUN` member support check cannot be upgraded by a combination declaration.
- Incomplete dependencies, conflicts, and overlapping components invalidate a declared combination.
- A duplicate combination-target pair is an invalid release, not a silently overwritten row.

### Task 1: Domain value and recommendation matching

**Files:** `catalog/model/VerifiedCombination.java`, `CatalogRelease.java`, `CatalogValidator.java`, `recommendation/RecommendationEngine.java`, `RecommendationEngineTest.java`.

- [x] Write tests for exact-target match and mismatches; update existing synthetic combination fixtures.
- [x] Run focused tests and observe missing combination type failure.
- [x] Implement immutable combination value, Catalog validation, and target-aware search.
- [x] Run focused tests again.

### Task 2: Configuration selection matching

**Files:** `recommendation/selection/ConfigurationSelectionValidator.java`, `ConfigurationSelectionValidatorTest.java`.

- [x] Write tests for exact-target multi-tool choice and a changed Client version.
- [x] Run focused tests and observe failure during the type change.
- [x] Use the target-aware combination value after the existing dependency/conflict/component checks.
- [x] Run focused tests again.

### Task 3: Catalog schema and evidence

**Files:** `catalog/CatalogSemanticParser.java`, `CatalogSemanticParserTest.java`, `Docs/fullstack-b/catalog/catalog-semantic-parser-plan.md`, `Docs/fullstack-b/STATUS.md`, `services/core-api/README.md`.

- [x] Write tests for valid synthetic targeted combination, duplicate pair, missing evidence, ambiguous support target, and unverified member support.
- [x] Run focused tests and observe failure, including the ambiguous support target case.
- [x] Parse target fields and retain evidence under a target-specific key.
- [x] Run full Maven package, inspect test counts, and run `git diff --check`. Result: 37 tests discovered, 36 passed, one Windows symlink test skipped; Java 21 class-file target (major 65) confirmed on JDK 24.
