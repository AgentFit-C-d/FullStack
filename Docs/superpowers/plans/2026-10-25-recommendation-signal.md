# Recommendation Signal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let explicit project work produce a recommendation without treating every unmentioned Capability as an unanswered question.

**Architecture:** AI returns all nine Capability assessments. `NOT_REQUESTED` means the confirmed facts contain no signal for that Capability; it is distinct from `UNDETERMINED`, which requires a specific unresolved question. Core validates the value, persists it, and keeps verified Catalog selection unchanged. skills.sh remains a discovery source until tool support and output are verified.

**Tech Stack:** FastAPI/Python, Spring Boot/Java 21, PostgreSQL/Flyway, React/Vite.

**Spec:** `Docs/fullstack-b/integration/ab-ai-contract-proposal.md`, `Docs/fullstack-b/recommendation/optional-candidates-contract-proposal.md`.

## Global Constraints

- Keep nine Capability keys and four recommendation statuses.
- Do not turn skills.sh candidates into approved recommendation items automatically.
- Preserve version/basis validation and project ownership.

## Review Focus

- Explicit browser testing needs only relevant Capability claims.
- Generic project facts must not generate nine unrelated questions.
- A truly unresolved fact still produces `NEEDS_INFORMATION`.
- Runtime metadata must not become a Skill need.
- PostgreSQL upgrades from V5–V7 must accept the new necessity value.

---

### Task 1: Define and verify Capability meaning

**Files:** `services/ai-service/app/ai_v2/needs.py`, `services/ai-service/tests/test_skill_needs_rank.py`, `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/ai/AiCapabilityIntake.java`, `services/core-api/src/test/java/com/agentfit/coreapi/recommendation/ai/AiCapabilityIntakeTest.java`.

- [x] Add failing AI and Java tests for `NOT_REQUESTED` and targeted questions.
- [x] Implement all-nine assessment with only explicit needs marked `REQUIRED`.
- [x] Verify both test groups.

### Task 2: Persist and transmit the value

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/persistence/JdbcRecommendationMetadataWriter.java`, `services/core-api/src/main/resources/db/migration/V8__recommendation_not_requested.sql`, `contracts/openapi/ai-service.yml`, Core/AI integration tests.

- [x] Add failing intake and adapter tests for `NOT_REQUESTED` and work-bearing facts.
- [x] Add the enum and migration; document the wire value.
- [x] Verify V8 upgrade and recommendation readback against PostgreSQL.

### Task 3: Remove context noise and confirm end-to-end result

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/AiCapabilityAnalyzerAdapter.java`, its tests, AI tests, `Docs/fullstack-b/integration/finalization-baseline-2026-10-25.md`.

- [x] Add failing test that OS, Client version, and developer version are not skill needs.
- [x] Restrict AI facts to work-bearing profile items and responsibilities.
- [x] Run relevant Core and AI tests, including live FastAPI + PostgreSQL recommendation and Preview.
- [x] Commit in Korean conventional format.
