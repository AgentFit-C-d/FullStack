# AI Analysis InputRef Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the stored planning input to the real AI Service through an attempt-bound `inputRef`, persist the result, and expose terminal analysis states to the existing frontend polling flow.

**Architecture:** Core API remains the system of record for projects, planning inputs, attempts, leases, and results. A scheduled Core worker claims processing attempts, sends a HMAC-signed request to AI Service, and validates the response context before persistence. AI Service validates the Core request, retrieves the original bytes through a lease-protected internal Core endpoint, and invokes the existing Python analysis service.

**Tech Stack:** Spring Boot 3 / Java 21, JdbcTemplate + Flyway PostgreSQL migrations, Java HttpClient/HMAC-SHA256, FastAPI/Python 3.11, existing provider/extraction/grounding modules, JUnit/MockMvc and pytest.

**Spec:** `Docs/superpowers/specs/2026-10-27-ai-analysis-inputref-pipeline-design.md`

## Global Constraints

- Browser authentication and project ownership stay in Core API.
- AI Service receives no browser cookies, GitHub tokens, or provider secrets.
- Original input limits remain 10 MiB, 100 PDF pages, and 100,000 code points.
- Raw input, extracted text, HMAC secrets, and provider keys must not be logged or returned in public responses.
- Start remains asynchronous: `POST /api/projects/{projectId}/analysis-attempts` returns `202 PROCESSING`.
- Recommendation functionality is out of scope.

## Review Focus

- A stale or mismatched lease cannot retrieve input bytes or persist a result.
- A PDF keeps its original bytes and page-aware extraction path.
- A provider timeout/failure releases admission capacity and becomes a safe terminal state.
- A mismatched/replayed AI response cannot complete the wrong attempt.
- Duplicate worker delivery cannot create duplicate terminal transitions or duplicate result rows.

### Task 1: Lease and result persistence contracts

**Files:**
- Create: `services/core-api/src/main/resources/db/migration/V5__analysis_input_lease_and_result.sql`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisAttempt.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/AnalysisAttemptRepository.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/JdbcAnalysisAttemptRepository.java`
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisResult.java`
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/AnalysisResultRepository.java`
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/JdbcAnalysisResultRepository.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisAttemptRepositoryTest.java`

**Interfaces:**
- Add attempt fields `inputUploadId`, `leaseId`, and `leaseExpiresAt` without breaking existing convenience constructors.
- Add `findProcessingAttempts()`, `claimForExecution(...)`, `completeTerminal(...)`, and `findByInputUploadId(...)` repository operations.
- Store one JSON result per attempt keyed by attempt ID/request ID.

- [ ] Write failing repository/entity tests for lease binding, terminal transition idempotency, and result uniqueness.
- [ ] Run the focused tests and confirm they fail because the new persistence contract is absent.
- [ ] Add the Flyway migration and JDBC implementations; keep existing test repositories compatible through default methods/constructors.
- [ ] Run the focused tests and the analysis unit suite.

### Task 2: Input reference creation and protected Core input endpoint

**Files:**
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisInputReference.java`
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/controller/InternalInputController.java`
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/InternalInputAccessService.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/HmacRequestSigner.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/InternalInputAccessTest.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/InternalInputControllerTest.java`

**Interfaces:**
- `AnalysisInputReference` contains opaque `uploadId`, `storage`, `objectKey`, and `expiresAt`.
- `InternalInputAccessService.issue(...)` binds the reference to project ID, input revision, attempt ID, and lease ID.
- `InternalInputController` exposes metadata and bytes only for a valid attempt/lease context; it never uses browser session authentication.

- [ ] Write failing tests for valid access, expired lease, wrong attempt, wrong upload ID, and metadata size/type response.
- [ ] Run focused tests to verify the unauthorized cases fail before implementation.
- [ ] Implement reference issuance and HMAC-authenticated internal access checks.
- [ ] Run focused tests and verify PDF bytes are returned unchanged while public endpoints remain metadata-only.

### Task 3: Core worker and AI request construction

**Files:**
- Create: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisExecutionScheduler.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisExecutionService.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClient.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisAttemptContext.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisExecutionSchedulerTest.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/HttpAiAnalysisClientTest.java`

**Interfaces:**
- Worker loads a processing attempt and current `PlanningInput`, builds the reference-bound request context, and calls `AnalysisExecutionService.executeAndPersist(...)`.
- AI request includes `requestId`, `generation`, `contractVersion`, `documentId`, `attemptId`, `leaseId`, `inputKind`, `inputRef`, `provider`, and `deadline`.
- The HMAC signature covers the exact serialized request body and endpoint path.

- [ ] Write failing tests proving the worker sends the selected input revision and handles timeout/provider failure.
- [ ] Run focused tests and verify the worker is not yet wired.
- [ ] Implement scheduling/claiming, input mapping, one retry only for explicit `503`, and terminal failure handling with admission release.
- [ ] Run the scheduler/client tests and the existing execution tests.

### Task 4: Real AI Service input retrieval and analysis execution

**Files:**
- Modify: `services/ai-service/app/main.py`
- Create: `services/ai-service/app/internal_input.py`
- Modify: `services/ai-service/app/analysis/service.py`
- Modify: `services/ai-service/app/settings.py`
- Modify: `services/ai-service/pyproject.toml`
- Modify: `services/ai-service/tests/conftest.py`
- Create: `services/ai-service/tests/test_internal_analysis_pipeline.py`

**Interfaces:**
- `/internal/v1/analysis` verifies the Core HMAC envelope, maps it to the existing `AnalysisService.analyze` request, and returns a validated analysis response.
- AI-to-Core input reads use the internal token/lease contract and bounded reads; no browser credentials are accepted.
- PDF input uses `layout=True` for the provider path that requires original PDF grounding; Markdown/text uses UTF-8 extraction.

- [ ] Write failing pytest cases for PDF retrieval, provider invocation, malformed input, HMAC failure, and result context echo.
- [ ] Run the Python tests and confirm the endpoint still returns the old test double, proving the new assertions are meaningful.
- [ ] Wire the endpoint to `AnalysisService`, add missing runtime dependencies/configuration, and preserve secret redaction.
- [ ] Run the complete AI Service pytest suite.

### Task 5: Result persistence, terminal state, and polling response

**Files:**
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisExecutionService.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/JdbcAnalysisAttemptRepository.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/dto/response/AnalysisAttemptResponse.java`
- Modify: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisResultPersistenceTest.java`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/ProjectAnalysisWebMvcTest.java`

**Interfaces:**
- Persist validated result JSON by attempt ID; public attempt responses expose only safe state/error metadata.
- `SUCCEEDED`, `FAILED`, and `TIMEOUT` transitions are atomic and idempotent.
- Raw input cleanup follows the existing scrub policy only after a terminal transition.

- [ ] Write failing tests for success persistence, provider failure, timeout cleanup, response mismatch, and duplicate completion.
- [ ] Run focused tests to verify the new terminal/result behavior is absent.
- [ ] Implement persistence and terminal transitions with admission release exactly once.
- [ ] Run all Core API analysis/project tests.

### Task 6: End-to-end contract verification and configuration

**Files:**
- Modify: `services/core-api/src/main/resources/application.yml`
- Modify: `services/ai-service/README.md`
- Modify: `contracts/openapi/ai-service.yml`
- Test: `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisPipelineContractTest.java`
- Test: `services/ai-service/tests/test_internal_analysis_contract.py`

- [ ] Write failing contract tests for the full request/response envelope and state transitions.
- [ ] Run the contract tests before wiring configuration and record expected failures.
- [ ] Align default local ports, HMAC settings, internal API origin, provider selection, and timeout values.
- [ ] Run Core API tests, AI Service tests, frontend tests, and frontend build.

