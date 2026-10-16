# Feature Specification: AI v2 Core Integration

**Feature Branch**: `codex/ai-v2-core-integration`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "AI v2의 핵심 분석 로직만 가져오고 FullStack API와 HMAC 인증은 유지한다"

## User Scenarios & Testing

### User Story 1 - Analyze an approved project input (Priority: P1)

When the system starts an analysis for an approved project input, it should use the AI v2 analysis capabilities and return a result in the existing FullStack format.

**Why this priority**: This is the primary value of the integration and enables the existing product flow to use the new analysis implementation without changing its callers.

**Independent Test**: Submit a synthetic, valid analysis request and verify that the existing response shape contains the mapped AI v2 result and original request context.

**Acceptance Scenarios**:

1. **Given** a valid approved input and valid internal authentication, **When** analysis is requested, **Then** the system returns a validated result in the existing FullStack contract.
2. **Given** a valid synthetic input without a configured paid provider key, **When** analysis is requested in test mode, **Then** the system returns the deterministic test result without calling a paid provider.

### User Story 2 - Reject unsafe or invalid analysis requests (Priority: P1)

The system must reject requests that are unauthenticated, tampered with, replayed, expired, malformed, or inconsistent with the request context.

**Why this priority**: Analysis may process private project material and must not weaken the existing service-to-service security boundary.

**Independent Test**: Exercise valid, tampered, stale, replayed, malformed, and provider-failure requests and verify the expected failure behavior for each.

**Acceptance Scenarios**:

1. **Given** an invalid or stale authentication signature, **When** analysis is requested, **Then** the request is rejected and no analysis result is saved.
2. **Given** a malformed provider response or expired deadline, **When** analysis is requested, **Then** the request fails safely and no successful analysis result is produced.

### User Story 3 - Preserve existing system boundaries (Priority: P2)

The product team can deploy the AI v2 analysis capability without moving project ownership, source-document access, budget decisions, or result persistence out of the FullStack backend.

**Why this priority**: Keeping these responsibilities in the existing backend prevents duplicated authorization and inconsistent project state.

**Independent Test**: Verify that the analysis service receives only the approved request projection and that ownership, input access, budget, and persistence remain controlled by the existing backend flow.

**Acceptance Scenarios**:

1. **Given** an analysis request, **When** the AI service processes it, **Then** it does not become the source of truth for project ownership or persistence.
2. **Given** a provider or validation failure, **When** the request completes, **Then** no raw source material, secret, or raw provider response is persisted or emitted to ordinary logs.

### Edge Cases

- Missing, empty, oversized, or unsupported input.
- Duplicate request ID or replayed signed request.
- Request deadline shorter than provider processing time.
- Provider timeout, rate limit, authentication failure, or malformed response.
- Missing evidence, conflicting evidence, or invalid evidence location.
- Test mode without a provider key.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST preserve the existing internal analysis entry point and authentication behavior for current callers.
- **FR-002**: The system MUST use the AI v2 document analysis, provider execution, and evidence-validation capabilities for approved analysis requests.
- **FR-003**: The system MUST translate existing FullStack analysis requests into the input expected by the AI v2 capabilities.
- **FR-004**: The system MUST translate validated AI v2 results into the existing FullStack response contract while preserving request context.
- **FR-005**: The system MUST reject invalid, expired, tampered, replayed, malformed, or deadline-exceeded requests.
- **FR-006**: Provider failures and invalid AI results MUST NOT create a successful analysis result.
- **FR-007**: Project ownership, source-input authorization, lease state, budget decisions, and persistence MUST remain controlled by the existing backend.
- **FR-008**: The system MUST NOT log or persist raw source documents, secrets, provider credentials, or raw provider responses.
- **FR-009**: The integration MUST support deterministic synthetic testing without requiring a paid provider call.
- **FR-010**: The initial integration MUST NOT include the separate Skill needs/ranking workflow.

### Key Entities

- **Analysis Request**: The approved, authenticated request context and input reference for one analysis attempt.
- **Analysis Result**: The validated profile, evidence, model metadata, status, and request context returned to the existing product flow.
- **Provider Failure**: A normalized failure category that prevents a successful analysis result from being produced.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of valid synthetic analysis requests produce a response in the existing contract without requiring a paid provider call.
- **SC-002**: 100% of invalid, tampered, replayed, expired, or malformed requests are rejected without a successful result.
- **SC-003**: 100% of provider or evidence-validation failures leave no new successful analysis result.
- **SC-004**: Existing analysis callers require no endpoint or authentication change.
- **SC-005**: Automated tests cover request mapping, response mapping, authentication failures, provider failures, deadline handling, and sensitive-data exclusion.

## Assumptions

- FullStack remains the authority for project ownership, private input access, budget reservation, leases, and persistence.
- The AI v2 repository is used as the source of reusable analysis logic, not as a replacement for the FullStack service boundary.
- Paid provider calls are enabled only in explicitly configured environments; tests use deterministic providers or test doubles.
- The first delivery covers document analysis only; Skill recommendation mapping is a later feature.
