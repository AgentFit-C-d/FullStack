# Feature Specification: A-B Phase 2 Integration

**Feature Branch**: `feat/fullstack-a-phase2`
**Created**: 2026-10-14
**Status**: Draft
**Input**: Connect A authentication and ownership validation to B recommendation and Preview APIs, transform A analysis results into B recommendation input, and make B persistence, Audit, and project deletion ordering transactional without changing B service interfaces or DTOs.

## Clarifications

### Session 2026-10-14

- Q: Phase 2 DB migration은 현재 코드 타입을 유지하고 자식 엔티티만 UUID로 적용할까요? → A: 문서 B — PDF 기준대로 `PROJECT`와 `USER`를 포함한 핵심 식별자를 UUID로 통일한다.
- Q: PDF 기준의 A15·B9·공통 Audit 테이블을 이번 Phase 2 migration에서 모두 생성할까요? → A: A — 전체 테이블과 FK·복합 제약·삭제 순서를 이번 migration 범위에 포함한다.
- Q: 기존 `USER`·`PROJECT` 데이터를 UUID 전환 migration에서 보존해야 할까요? → A: B — 개발 DB는 폐기하고 UUID 기준의 전체 schema를 새로 생성한다.
- Q: 추천·Preview 공개 API는 PDF의 제안 경로를 기준으로 연결할까요? → A: PDF 경로 사용 — 프로젝트 하위에 `POST /recommendations`, `GET /recommendations/latest`, `POST /config-previews`를 사용한다.
- Q: UUID schema는 기존 V1~V4 개발 DB를 변환할까요? → 아니오 — 기존 개발 DB를 폐기하고 새 빈 DB를 생성한 뒤 UUID 기준 전체 schema migration을 적용한다. 기존 DB에 in-place migration을 수행하지 않는다.
- Q: 실패·거부 Audit은 업무 transaction과 어떻게 처리할까요? → 업무 rollback 이후 별도 최소 Audit transaction으로 기록하며, 원문·Secret·raw AI 응답은 저장하지 않는다.
- Q: 실패 Audit 대상은 무엇인가요? → 인증 실패, 프로젝트 소유권·참조 불일치, 삭제 프로젝트 접근, stale·version 충돌, 미확정·불완전·UNDETERMINED 분석, AI 호출·응답 검증 실패, Catalog unavailable·hash 불일치·지원되지 않는 조합, 활성 작업으로 인한 삭제 거부, 삭제 transaction/FK/lock 실패를 대상으로 한다.

## User Scenarios & Testing

### User Story 1 - Authenticated recommendation (Priority: P1)

An authenticated user requests a recommendation for one of their projects. The system validates identity, ownership, confirmed A analysis state, and current versions before invoking AI/B logic and saving the recommendation.

**Why this priority**: Recommendation is the first A-to-B value path and must not expose or mutate another user's project.

**Independent Test**: Test an owned project, a foreign project, an unauthenticated request, and a stale project version.

**Acceptance Scenarios**:

1. **Given** an authenticated owner with a confirmed analysis snapshot, **When** recommendation is requested, **Then** A values are converted to B input and one B recommendation plus its Audit record is committed atomically.
2. **Given** a foreign, deleted, unauthenticated, stale, or unconfirmed project, **When** recommendation is requested, **Then** the request is rejected and no B recommendation is saved.

### User Story 2 - Preview from a trusted recommendation (Priority: P2)

An authenticated owner requests a Preview using a recommendation belonging to the same project. The system rechecks ownership, recommendation membership, current basis, environment, and approved Catalog before saving the Preview.

**Why this priority**: Preview is the next user-visible B workflow and is unsafe without a second ownership and freshness check.

**Independent Test**: Test a valid recommendation, a recommendation from another project, stale basis, unsupported selection, and Catalog mismatch.

**Acceptance Scenarios**:

1. **Given** a current owned recommendation and valid Catalog, **When** Preview is requested, **Then** B validates the selection and persists Preview metadata and Audit atomically.

### User Story 3 - Safe project deletion (Priority: P1)

An authenticated owner explicitly confirms project deletion. The system locks and validates the project, removes B dependents and Audit data in the documented order, and deletes the A project in one transaction.

**Why this priority**: Deletion is a data-integrity and privacy requirement; partial deletion or late result resurrection is unacceptable.

**Independent Test**: Test a populated project, foreign owner, active analysis, and a delayed analysis/recommendation result.

**Acceptance Scenarios**:

1. **Given** an explicitly confirmed owned project with no blocking active operation, **When** deletion is requested, **Then** B children, B Audit, A children, A Audit, and the project are removed in order and the transaction commits.
2. **Given** any validation or deletion failure, **When** deletion is requested, **Then** the business transaction rolls back and the project remains inaccessible to the caller.

### Edge Cases

- AI returns a malformed, failed, incomplete, or undetermined capability assessment.
- A project, recommendation, or Preview is deleted or changes version while an external AI call is in flight.
- A recommendation ID, Preview ID, Catalog hash, or project ID belongs to another project.
- B persistence succeeds but Audit persistence fails, or deletion encounters an FK/locking failure.
- A late analysis or recommendation result arrives after project deletion.

## Requirements

### Functional Requirements

- **FR-001**: Every recommendation and Preview request MUST resolve the authenticated principal server-side and verify project ownership before reading A or B project data.
- **FR-002**: Recommendation input MUST be assembled only from the approved A analysis snapshot, confirmed values, allowed evidence paths, current environment, and server-approved Catalog context; raw document text, secrets, and raw AI responses MUST NOT be copied to DTOs, Audit, or ordinary logs.
- **FR-003**: The A-to-B adapter MUST preserve B's existing service interfaces, DTOs, and validation semantics; changes MUST be limited to adapters, orchestration, persistence wiring, and tests.
- **FR-004**: AI failure, malformed response, missing/undetermined capability, ownership failure, stale basis, or Catalog failure MUST NOT create a successful new recommendation and MUST preserve the last successful recommendation.
- **FR-005**: Preview creation MUST revalidate recommendation ownership/membership, basis versions, environment target, selected tools, approved Catalog hash, and required capability coverage immediately before persistence.
- **FR-006**: Successful B recommendation/Preview persistence and its common Audit event MUST commit in the same database transaction as the associated A workflow state.
- **FR-007**: Project deletion MUST lock the owned project, reject active blocking analysis, delete B dependent data before the A project, delete project-scoped Audit data, and reject late results after deletion.
- **FR-008**: The following failure or denial events MUST be recorded through a safe minimal path after business rollback in a separate transaction: authentication failure, project ownership/reference mismatch, deleted-project access, stale/version conflict, unconfirmed/incomplete/UNDETERMINED analysis, AI call/response validation failure, Catalog unavailable/hash mismatch/unsupported selection, active-operation deletion denial, and deletion transaction/FK/lock failure. The event MUST exclude raw input, secrets, and raw AI responses.
- **FR-009**: Public API behavior MUST be documented in the existing OpenAPI contracts and tested with success and failure fixtures; B service interfaces and DTO shapes remain unchanged.
- **FR-010**: The Phase 2 database migration MUST use UUID identifiers for `USER`, `PROJECT`, A profile/review/environment entities, B recommendation/Preview entities, and their cross-domain foreign keys according to the baseline document.
- **FR-011**: The Phase 2 database migration MUST create all A15, B9, and common Audit tables required by the baseline, including their foreign keys, uniqueness constraints, state checks, and documented project deletion order.
- **FR-012**: The migration MUST target a fresh development database; existing development `USER`, `PROJECT`, session, and dependent rows do not require data preservation or ID mapping.
- **FR-013**: Recommendation and Preview public endpoints MUST use `POST /api/projects/{projectId}/recommendations`, `GET /api/projects/{projectId}/recommendations/latest`, and `POST /api/projects/{projectId}/config-previews`, with exact fields, nullable rules, and errors aligned in the Core API OpenAPI contract tests.
- **FR-014**: Project deletion MUST follow the explicit order `PROJECT lock → active-operation check → B children → B Audit → A children → A Audit → PROJECT reference release → PROJECT delete` within one caller-owned database transaction; FK cascade alone is insufficient.

### Key Entities

- **Authenticated project snapshot**: An owner-checked, versioned read of A's confirmed analysis/profile, review, developer, environment, and project basis used for one B operation.
- **Recommendation input**: The minimal validated projection of the A snapshot, AI capability assessment, current environment, and approved Catalog data consumed by existing B recommendation services.
- **Preview basis**: The immutable project/recommendation/version/Catalog context that B uses to validate and persist a Preview.
- **Audit event**: The project-scoped success or safe failure event committed with the business state where required.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of recommendation and Preview requests for a foreign, unauthenticated, deleted, stale, or unconfirmed project are rejected without a new B row.
- **SC-002**: 100% of successful recommendation/Preview writes have a matching success Audit event; injected persistence failure leaves neither business write nor success Audit committed.
- **SC-003**: 100% of deletion tests remove all project-scoped B data before the A project and reject a late result after deletion.
- **SC-004**: Existing B unit and integration tests remain green, and all new A-B boundary tests pass without changing existing B DTO or service signatures.

## Assumptions

- The current B implementation is the code and tests present after the B phase1 merge into `origin/develop`; it owns Catalog validation, recommendation, Preview, and B persistence ports but does not own A authentication or project ownership.
- The PDF schema is the canonical Phase 2 identifier model; existing A `PROJECT VARCHAR(36)` and `USER BIGINT` persistence code must be migrated or adapted to UUID rather than treated as the final schema.
- Authentication, ownership, A snapshot reads, and project-level transaction coordination remain A responsibilities inside the same Core API process.
- The Phase 2 UUID schema is applied only to a newly created empty development database; existing V1~V4 development data is discarded and is not converted in place.
- External AI calls occur outside the database transaction; ownership, versions, basis, and deletion state are rechecked before persistence.
- Existing B public service interfaces and DTOs are fixed. A adapters may implement existing B ports without widening their contracts.
- The PDF is a development baseline and marks several A-B wire contracts and acceptance cases as proposal/NOT_RUN; this feature records those as implementation gates, not as evidence of prior integration.
- Exact public endpoint names and error envelopes follow the current OpenAPI files after the A-B adapter is added; no endpoint is treated as live until contract tests pass.
