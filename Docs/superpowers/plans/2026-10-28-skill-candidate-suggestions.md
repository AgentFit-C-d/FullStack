# Skill Candidate Suggestions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect stored project Capability claims to skills.sh discovery candidates with transparent, non-compatibility suggestions.

**Architecture:** A's existing session and ownership guard protects a new B endpoint. A pure B matcher maps candidate names to related Capability keys and reports uncovered requirements; the frontend displays results next to the saved recommendation and reuses the pinned candidate ZIP.

**Tech Stack:** Java 21, Spring Boot, MockMvc, React/Vite.

**Spec:** `Docs/superpowers/specs/2026-10-28-skill-candidate-suggestions.md`

## Global Constraints

- Modify B-owned code and frontend integration only; do not edit A or AI implementation.
- Never label discovery metadata as proven OS, Claude Code version, permission, or combination support.
- Require project ownership before reading stored Capability claims.

## Review Focus

- Missing latest recommendation: return a clear absence, not an empty successful match.
- Stale recommendation: return no suggestions until the user refreshes it.
- Unknown Capability: reject rather than silently ignore.
- Duplicate sources and tie ordering: preserve distinct IDs with deterministic sorting.
- No candidate covers a required Capability: report the unmet key.

### Task 1: Pure candidate relevance and coverage

**Files:** Create `services/core-api/src/main/java/com/agentfit/coreapi/catalog/discovery/SkillCandidateSuggestionMatcher.java`; test beside it.

- [x] Write tests for relevant test-authoring/execution candidates, deterministic ordering, and unmet requirements.
- [x] Run the test and confirm the expected failure.
- [x] Implement bounded name-rule matching with explicit `NAME_RULE` evidence and `UNCONFIRMED` combination status.
- [x] Run the targeted test and commit.

### Task 2: Authenticated project endpoint

**Files:** Create `SkillCandidateSuggestionController.java` in the same package; test with MockMvc; update `contracts/openapi/core-api.yml`.

- [x] Write failing tests for ownership, latest recommendation, stale result, and response body.
- [x] Run the test and confirm the expected failure.
- [x] Read latest persisted capabilities through `RecommendationLatestDetailsService`; load the candidate snapshot and call the matcher.
- [x] Run the targeted test and commit.

### Task 3: Frontend connection and runtime verification

**Files:** Modify `apps/web/src/features/live/LivePage.jsx`; update catalog documentation.

- [x] Reuse the existing API helper; no new standalone frontend helper was needed.
- [x] Fetch suggestions after a current recommendation is loaded; display related candidates and unmet keys with the existing ZIP action.
- [x] Run frontend tests/build/lint, targeted Java tests, and a local HTTP smoke check.
- [x] Commit and push the branch.
