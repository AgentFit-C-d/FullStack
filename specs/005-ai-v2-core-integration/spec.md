# Feature Specification: AI v2 Core Integration

**Feature Branch**: `codex/ai-v2-core-integration`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "[AgentFit_AI_v2](https://github.com/AgentFit-C-d/AgentFit_AI_v2)에서 AI 핵심 로직만 가져오고, API는 우리 방식으로 유지해서 FullStack AI 서비스와 연결한다"

**Source Repository**: [AgentFit_AI_v2](https://github.com/AgentFit-C-d/AgentFit_AI_v2)

## Clarifications

### Session 2026-10-16

- Q: 이번 AI v2 연결에 실제 문서 저장·조회 기능까지 포함할까요? → A: 분석 중 임시 보관만 하고, 분석 결과가 저장되면 원문은 폐기한다.
- Q: A 분석과 B 추천은 한 번에 실행할까요, 별도 단계로 실행할까요? → A: A 분석을 먼저 완료·저장한 뒤 사용자가 별도의 추천 API를 호출한다.
- Q: B 추천 전에 A 분석 결과를 누가 확정할까요? → A: 사용자가 분석 결과를 확인하고 직접 확정한다.
- Q: 정상적인 추천 상태와 AI·연동 실패를 구분할까요? → A: 구분한다. `NEEDS_INFORMATION`과 `NO_COMPATIBLE_TOOLS`는 정상적인 추천 결과이고, AI·계약·저장 실패는 별도 실패로 처리한다.

## User Scenarios & Testing

### User Story 1 - Analyze an approved project input and produce recommendation capabilities (Priority: P1)

When the system starts an analysis for an approved project input, it should use the AI v2 analysis capabilities and save a validated A result. A later, separate recommendation request uses the verified A result to produce the capability assessment that FullStack B's recommendation flow consumes.

**Why this priority**: This is the primary value of the integration and enables the existing product flow to use the new analysis implementation without changing its callers.

**Independent Test**: Complete a synthetic analysis request, verify the saved A result, then call the separate recommendation endpoint and verify that B consumes the verified result.

**Acceptance Scenarios**:

1. **Given** a valid approved input and valid internal authentication, **When** analysis is requested, **Then** the system saves a validated A result in the existing FullStack contract.
2. **Given** a completed A result that the user has reviewed and confirmed, **When** the user calls the recommendation endpoint, **Then** AI v2 capability output is mapped and B creates the recommendation.
3. **Given** a valid synthetic input without a configured paid provider key, **When** analysis is requested in test mode, **Then** the system returns the deterministic test result without calling a paid provider.

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
- **FR-003**: The system MUST translate existing FullStack analysis requests and transient document content into the input expected by the AI v2 capabilities.
- **FR-004**: The system MUST translate validated AI v2 results into the existing FullStack response contract while preserving request context.
- **FR-005**: The system MUST reject invalid, expired, tampered, replayed, malformed, or deadline-exceeded requests.
- **FR-006**: Provider failures and invalid AI results MUST NOT create a successful analysis result.
- **FR-007**: Project ownership, source-input authorization, lease state, budget decisions, and persistence MUST remain controlled by the existing backend.
- **FR-008**: The system MUST NOT log or persist raw source documents, secrets, provider credentials, or raw provider responses.
- **FR-009**: The integration MUST support deterministic synthetic testing without requiring a paid provider call.
- **FR-010**: The integration MUST connect AI v2's needs and candidate-ranking capabilities to the existing B capability assessment flow through an adapter; those provider-specific endpoints MUST NOT become public FullStack endpoints.
- **FR-011**: The integration MUST reuse AI v2 analysis logic without importing its Express API, Bearer authentication, web layer, or deployment-specific configuration.
- **FR-012**: The system MUST NOT retain raw source documents after the one-time analysis flow completes; transient document content MUST be discarded after the result is saved or the analysis fails.
- **FR-013**: The system MUST retain the structured analysis result and necessary audit metadata without retaining the original document content.
- **FR-014**: B recommendation creation MUST be a separate operation that requires a completed, user-confirmed A analysis result; an incomplete, failed, stale, or unconfirmed result MUST be rejected.
- **FR-015**: B MUST return `NEEDS_INFORMATION` when decision-changing information is unresolved, with no recommendation items and one or more clarification questions.
- **FR-016**: B MUST return `NO_COMPATIBLE_TOOLS` when information is sufficient but no verified tool combination satisfies all required capabilities, with no recommendation items and a reason.
- **FR-017**: AI provider failure, timeout, parsing failure, invalid identifier, contract mismatch, version conflict, and persistence failure MUST be represented as a separate failure outcome and MUST NOT be converted into `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`, or an apparently successful empty recommendation.
- **FR-018**: A failed new recommendation MUST preserve the last successful recommendation and its basis.

### Key Entities

- **Analysis Request**: The approved, authenticated request context and input reference for one analysis attempt.
- **Analysis Result**: The validated profile, evidence, model metadata, status, and request context returned to the existing product flow.
- **Provider Failure**: A normalized failure category that prevents a successful analysis result from being produced.
- **Transient Analysis Input**: Document content held only for the duration of one approved analysis request and not retained as a document record.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of valid synthetic analysis requests produce a response in the existing contract without requiring a paid provider call.
- **SC-002**: 100% of invalid, tampered, replayed, expired, or malformed requests are rejected without a successful result.
- **SC-003**: 100% of provider or evidence-validation failures leave no new successful analysis result.
- **SC-004**: Existing analysis callers require no endpoint or authentication change.
- **SC-005**: Automated tests cover request mapping, response mapping, B capability mapping, authentication failures, provider failures, deadline handling, and sensitive-data exclusion.
- **SC-006**: Tests verify that raw source content is removed after analysis completion or failure and is not retained in the database, ordinary logs, or durable service storage.
- **SC-007**: A valid AI capability assessment reaches B's existing recommendation engine without changing B's capability keys, validation rules, or public recommendation API.
- **SC-008**: A recommendation request cannot create a B recommendation before a completed and user-confirmed A result exists.
- **SC-009**: Tests distinguish `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`, and integration failure outcomes, including preservation of the last successful recommendation after a failed new attempt.

## Assumptions

- FullStack remains the authority for project ownership, private input access, budget reservation, leases, and persistence.
- The AI v2 repository is used as the source of reusable analysis logic, not as a replacement for the FullStack service boundary.
- Raw source documents are retained only for the one-time analysis operation; the structured result and audit metadata remain after the source is removed.
- Paid provider calls are enabled only in explicitly configured environments; tests use deterministic providers or test doubles.
- AI v2's needs/rank behavior is used internally to produce B's existing capability claims and questions; the public API remains FullStack's recommendation API.
