---
description: "분석 Worker 연결과 A→확인→B Docker 데모 구현 작업"
---

# Tasks: 분석 Worker 연결과 A→확인→B 데모 흐름 완성

**Input**: `specs/006-analysis-worker-demo/`의 plan.md, spec.md, research.md, data-model.md, contracts/

**전제**: 구현은 대상 브랜치 `codex/analysis-worker-demo`에서 수행한다. 현재 체크아웃이 다른
브랜치라면 코드 작업 전에 분리한다.

**테스트 원칙**: 헌장에 따라 핵심 규칙은 실패하는 테스트를 먼저 만든다. 외부 AI 호출은
transaction 밖에서 한 번만 수행하며, PostgreSQL이 없는 H2 통과만으로 완료 처리하지 않는다.

## Format: `[ID] [P?] [Story] 설명`

- **[P]**: 서로 다른 파일이라 선행 의존성 없이 병렬 수행 가능
- **[US#]**: 해당 사용자 스토리와 연결된 작업

## Phase 1: 준비

**목적**: 새 기능의 실제 작업 공간과 검증 기준을 고정한다.

- [X] T001 `specs/006-analysis-worker-demo/plan.md`의 대상 브랜치 `codex/analysis-worker-demo`를 위한 분리 작업 공간을 만들고, 구현 전 `git status --short`로 기존 변경을 보존할 수 있는지 확인한다.
- [ ] T002 [P] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerPostgresSchemaIntegrationTest.java`에 fresh Flyway PostgreSQL에서 UUID 로그인 사용자 기준으로 Project 생성·소유권 조회·PlanningInput 저장/조회·분석 시작과 `analysis_attempt`, `planning_input`, `analysis_admission` SQL이 실행되지 못하는 현재 불일치 회귀 테스트를 작성한다.
- [X] T003 [P] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisExecutionServiceTest.java`에 HTTP 503 `NotAcceptedException`도 외부 AI 호출을 정확히 1회만 수행하고 실패로 전파해야 한다는 실패 테스트를 작성한다.

---

## Phase 2: 공통 기반 — PostgreSQL 정합성과 실행 claim

**목적**: 모든 사용자 스토리를 막는 UUID/status schema, 원문 입력, admission, Worker claim 기반을 완성한다.

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 Worker나 Docker 데모를 구현하지 않는다.

- [ ] T004 `services/core-api/src/main/resources/db/migration/V6__analysis_worker_postgres_alignment.sql`에 V5 기준 UUID `analysis_attempt`의 UUID `owner_id`, `input_revision`, `expected_project_version`, `expected_basis`, `execution_token`, `claimed_at`, `lease_expires_at`와 UUID `planning_input`, `analysis_admission`, UUID `project_creation_idempotency`의 fresh PostgreSQL DDL·제약·인덱스를 추가한다.
- [ ] T005 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisAttempt.java`와 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisAttemptContext.java`를 UUID `id`, `status`, `finished_at`, claim token, `input_revision`, expected project version/basis의 상태 모델로 변경한다.
- [ ] T006 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/AnalysisAttemptRepository.java`에 unclaimed PROCESSING claim, claim 만료 조회, token 조건부 성공/실패 terminal 전환 API를 정의한다.
- [ ] T007 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/JdbcAnalysisAttemptRepository.java`의 `Long id`·`state`·`completed_at` SQL을 UUID·`status`·`finished_at`로 교체하고, `id + status=PROCESSING + execution_token` 조건부 갱신을 구현한다.
- [ ] T008 `services/core-api/src/main/java/com/agentfit/coreapi/domain/auth/`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/audit/`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/dto/response/AnalysisAttemptResponse.java`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`를 UUID principal·Project·PlanningInput·Audit·attempt 생성/응답 및 V5/V6 컬럼명에 맞춘다.
- [ ] T009 `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerPostgresSchemaIntegrationTest.java`에서 fresh Flyway migration 후 UUID attempt 생성, expected project version/basis 저장, 단일 claim, status/finished_at terminal 전환, PlanningInput scrub, admission lock을 검증한다.
- [ ] T010 `services/core-api/src/main/resources/application.yml`에 Worker polling 기본값 1초와 claim lease 기본값 180초 이상을 추가하고, 테스트 환경에서 새 `PROCESSING` attempt가 2초 안에 claim되는지 검증한다. `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisTimeoutScheduler.java`는 timeout terminal 처리에 새 Repository API만 사용하도록 조정한다.

**Checkpoint**: fresh PostgreSQL에서 A 분석 attempt를 생성·claim·terminal 처리할 수 있고, Java SQL에 `state`·BIGSERIAL 가정이 남아 있지 않다.

---

## Phase 3: User Story 1 — 분석 요청을 실제 AI 처리로 완료한다 (Priority: P1) 🎯 MVP

**목표**: 202 `PROCESSING` 요청이 주기 Worker를 통해 실제 HMAC AI 호출, 응답 검증, DRAFT 저장,
`SUCCEEDED`까지 도달하게 한다.

**독립 검증**: 가짜 HMAC AI client로 시작한 UUID attempt가 Worker 한 tick 후 정확히 한 번 호출되고,
유효한 응답이면 DRAFT와 `SUCCEEDED`가 저장되는지 확인한다.

### Tests for User Story 1

- [ ] T011 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerTest.java`에 두 Worker가 같은 PROCESSING attempt를 볼 때 정확히 하나만 claim하고 AI client를 한 번 호출한다는 실패 테스트를 작성한다.
- [ ] T012 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisExecutionServiceTest.java`에 응답의 requestId·projectId·generation·contractVersion·deadline·필수 DRAFT field 검증 실패와 금지 field(`purpose`, `target_users`, `constraints` 및 별칭) 포함이 DRAFT 저장 전 예외가 되는 테스트를 추가한다.
- [ ] T013 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerPersistenceIntegrationTest.java`에 성공 terminal이 "DRAFT 저장 + `SUCCEEDED` + result draft id/version + admission 해제 + 최소 Audit"을 하나의 PostgreSQL transaction으로 만들고, AI 호출 뒤 PROJECT version/basis·generation 변경 또는 삭제가 일어나면 DRAFT/`SUCCEEDED`를 만들지 않고 기존 CONFIRMED Profile·성공 추천을 보존하며 해당 input revision을 scrub하는 테스트를 작성한다.

### Implementation for User Story 1

- [ ] T014 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisExecutionService.java`에서 `NotAcceptedException`의 즉시 재호출을 제거하고, Core-owned PlanningInput을 transient base64 입력으로 한 번만 변환·호출하도록 유지한다.
- [ ] T015 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisWorker.java`를 `@Scheduled` 컴포넌트로 추가해 설정 주기마다 unclaimed PROCESSING attempt 하나를 claim하고 Core-owned PlanningInput을 읽어 실행하게 한다.
- [ ] T016 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`에 claim token·PROJECT lock·expected project version/basis·소유권·프로젝트 generation·deadline을 재검증한 성공 terminal coordinator를 추가해 DRAFT 저장, `SUCCEEDED`, result draft 연결, admission 해제, Audit을 한 transaction으로 처리하고, 불일치·삭제 시 `STALE_ANALYSIS_RESULT` 실패 terminal로 전환한다.
- [ ] T017 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisResultPersistenceService.java`가 [005 기준 10개 허용 planning field](../005-ai-v2-core-integration/spec.md)만 DRAFT에 쓰고 금지 field(`purpose`, `target_users`, `constraints` 및 별칭)·원문·추출문·원시 AI 응답을 쓰지 않는지 보강한다.
- [ ] T018 [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerTest.java`와 `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerPersistenceIntegrationTest.java`를 통과시켜 PROCESSING→SUCCEEDED 전체 경로와 중복 호출 방지를 확인한다.

**Checkpoint**: User Story 1은 프론트엔드 없이 valid input 1건을 Worker가 처리해 구조화된 A DRAFT와 `SUCCEEDED`를 만드는 상태다.

---

## Phase 4: User Story 2 — terminal 처리에서 원문을 제거한다 (Priority: P1)

**목표**: 성공·AI 오류·계약 오류·timeout·Worker 중단의 모든 terminal 경로에서 같은 입력 revision의
raw content·extracted text·citations을 제거한다.

**독립 검증**: 각 terminal 결과 뒤 해당 PlanningInput revision의 세 원문 필드가 NULL이고, 성공한
경우에만 구조화된 DRAFT가 남는지 PostgreSQL에서 확인한다.

### Tests for User Story 2

- [ ] T019 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerCleanupTest.java`에 provider/transport/계약/저장 실패가 `FAILED`·최소 error code·PlanningInput scrub을 남기고 성공 DRAFT를 만들지 않는 실패 테스트를 작성한다.
- [ ] T020 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerCleanupTest.java`에 deadline 경과는 `FAILED/TIMEOUT`, claim lease 만료는 `FAILED/WORKER_INTERRUPTED`로 종료하며 같은 attempt를 다시 AI 호출하지 않는 테스트를 작성한다.
- [ ] T021 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/audit/AuditRecorderTest.java`에 analysis Worker의 Audit metadata가 raw content, extracted text, citations, Solar key, HMAC secret, 원시 AI 응답을 포함하지 않는 테스트를 추가한다.

### Implementation for User Story 2

- [ ] T022 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`에 token 조건부 실패 terminal coordinator를 구현해 `FAILED`, PlanningInput scrub, admission 해제, 최소 Audit을 하나의 transaction으로 처리한다.
- [ ] T023 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisWorker.java`와 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisTimeoutScheduler.java`에서 AI/계약/저장 오류, deadline, 만료 claim을 정규화된 error code로 T022에 전달한다.
- [ ] T024 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/repository/JdbcPlanningInputRepository.java`의 scrub이 attempt의 정확한 `project_id + input_revision`에만 적용되고 raw_content·extracted_text·citations을 함께 NULL 처리하는지 검증·수정한다.
- [ ] T025 [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisWorkerCleanupTest.java`와 `services/core-api/src/test/java/com/agentfit/coreapi/domain/audit/AuditRecorderTest.java`를 통과시켜 모든 terminal 경로의 원문 수명과 로그 제외를 확인한다.

**Checkpoint**: User Story 2는 terminal 뒤 원문을 남기지 않으며, 사용자 확인용 DRAFT만 성공 경로에 남기는 상태다.

---

## Phase 5: User Story 3 — 확정 A 결과로 B 추천을 실행한다 (Priority: P1)

**목표**: Worker가 만든 DRAFT를 사용자가 확정한 뒤에만 B 추천을 실행하고, 미확정 거부·정상 B
결과·연동 실패를 구분한다.

**독립 검증**: Worker 성공 DRAFT가 있어도 확정 전 추천은 `PROFILE_NOT_CONFIRMED`이고, 확정 후에는
`RECOMMENDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS` 중 정상 결과 하나가 만들어지는지 확인한다.

### Tests for User Story 3

- [ ] T026 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProfileConfirmationServiceTest.java`에 Worker가 저장한 DRAFT를 확정해도 DRAFT가 삭제되지 않고 CONFIRMED Profile·확인 이벤트·Audit이 함께 만들어지는 회귀 테스트를 추가한다.
- [ ] T027 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/catalog/AiV2AnalysisToRecommendationIntegrationTest.java`에 미확정 추천의 `PROFILE_NOT_CONFIRMED`, 유효한 `UNDETERMINED`+질문의 `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`가 AI/저장 실패와 구분되고, 알 수 없거나 중복된 Capability ID·허용되지 않은 source field·금지 planning field/별칭은 연동 `FAILED`가 되는 테스트를 추가한다.

### Implementation for User Story 3

- [ ] T028 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisResultPersistenceService.java`와 `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProfileConfirmationService.java`의 DRAFT/version/result attempt 연결이 Worker 성공 뒤 사용자 확정 흐름과 일치하도록 보완한다.
- [ ] T029 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/catalog/RecommendationCreationService.java`, `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/JdbcRecommendationStoreAdapter.java`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/controller/ProjectController.java`에서 기존 공개 recommendation 계약을 바꾸지 않고 CONFIRMED Profile만 B로 전달하는지 회귀 보완한다.
- [ ] T030 [US3] `services/core-api/src/test/java/com/agentfit/coreapi/catalog/AiV2AnalysisToRecommendationIntegrationTest.java`와 `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProfileConfirmationServiceTest.java`를 통과시켜 A DRAFT→확정→정상 B 결과 경로를 확인한다.

**Checkpoint**: User Story 3은 B Catalog가 비어 있어도 `NO_COMPATIBLE_TOOLS`를 정상 결과로 구분하며, A 미확정 상태에서는 추천을 만들지 않는다.

---

## Phase 6: User Story 4 — Docker에서 Solar 포함 전체 백엔드 데모를 검증한다 (Priority: P2)

**목표**: Docker Compose에서 Core의 세 내부 AI URL, HMAC, 결정적 제공자, Solar 실제 1회 호출을
설정하고 A→확인→B 데모를 재현한다.

**독립 검증**: Compose config가 모든 URL/provider 값을 컨테이너에 전달하고, 결정적 회귀 테스트와
Solar 작은 텍스트 1회 smoke가 모두 가능하다.

### Tests for User Story 4

- [ ] T031 [P] [US4] `services/ai-service/tests/test_main_provider.py`에 `AGENTFIT_AI_V2_PROVIDER=solar`와 `UPSTAGE_API_KEY`가 SolarProvider를 선택하고 키 자체가 응답·로그 데이터에 포함되지 않는 테스트를 작성한다.
- [ ] T032 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClientTest.java`에 analysis·needs·rank 각각이 설정된 별도 URL과 동일 HMAC signing path를 사용하는 계약 회귀 테스트를 추가한다.

### Implementation for User Story 4

- [ ] T033 [US4] `infra/compose.fullstack.yaml`의 Core environment에 `AGENTFIT_AI_SERVICE_ANALYSIS_URL`, `AGENTFIT_AI_SERVICE_NEEDS_URL`, `AGENTFIT_AI_SERVICE_RANK_URL`을 `ai-service:8001` 경로로 추가하고 기존 analysis 단일 별칭 의존을 제거한다.
- [ ] T034 [US4] `infra/compose.fullstack.yaml`의 `ai-service` environment에 `AGENTFIT_AI_V2_PROVIDER`와 `UPSTAGE_API_KEY`의 Compose substitution을 추가하며, 실제 값은 포함하지 않는다.
- [ ] T035 [US4] `.env.docker.example`, `README.md`, `services/ai-service/README.md`에 Solar provider/key 변수 이름, 기본 deterministic 동작, `.env.docker` Git 제외, 세 내부 URL의 역할을 문서화한다.
- [ ] T036 [US4] `services/ai-service/tests/test_main_provider.py`와 `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClientTest.java`를 통과시켜 provider 선택과 세 HMAC route를 확인한다.
- [ ] T037 [US4] `specs/006-analysis-worker-demo/quickstart.md` 절차대로 `docker compose --env-file .env.docker -f infra/compose.fullstack.yaml config --no-interpolate`와 Compose health check를 실행해 실제 Solar/HMAC secret을 출력·inspect하지 않고 Core·AI·PostgreSQL 연결을 확인한다. 이어서 공개 endpoint 추가 없이 Core의 기존 AI client/service로 analysis·needs·rank 세 내부 경로를 각각 HMAC 호출해 2xx와 request context 일치를 확인한다. 실제 key 전달은 T038 Solar smoke 성공으로만 검증한다.
- [ ] T038 [US4] `specs/006-analysis-worker-demo/quickstart.md` 절차대로 작은 텍스트로 Solar 실제 A 분석 1회, DRAFT 확인, 원문 scrub, 사용자 확정, B의 `RECOMMENDED`/`NEEDS_INFORMATION`/`NO_COMPATIBLE_TOOLS` 정상 결과 1건을 기록한다.

**Checkpoint**: User Story 4는 deterministic 회귀와 실제 Solar smoke를 모두 통과하고, B 담당자에게 전달할 Docker 실행 증거가 있는 상태다.

---

## Phase 7: 마무리와 인계

**목적**: 전체 회귀, 계약 정합성, 독립 리뷰 증거를 남기고 A 인계 조건을 판정한다.

- [ ] T039 [P] `contracts/openapi/core-api.yml`, `contracts/openapi/ai-service.yml`, `specs/006-analysis-worker-demo/contracts/analysis-worker.md`를 대조해 공개 API·HMAC 경로·UUID attempt 응답이 구현 및 fixture와 일치하는지 검토하고 필요한 계약/fixture를 갱신한다.
- [ ] T040 [P] `services/core-api/pom.xml`과 `services/ai-service/pyproject.toml`의 기존 명령으로 Core 전체 Maven 테스트와 AI pytest 전체 회귀를 실행하고 결과를 `specs/006-analysis-worker-demo/quickstart.md` 검증 기록에 반영한다.
- [ ] T041 `specs/006-analysis-worker-demo/spec.md`의 Handoff Criteria 세 항목을 T018, T025, T038의 증거로 재검토하고, 미충족 항목이 있으면 완료로 표시하지 않는다.
- [ ] T042 `specs/006-analysis-worker-demo/review.md`에 구현자와 독립된 코드 리뷰 결과(Worker 중복 실행, terminal 원자성, 원문/Secret 제외, UUID SQL, HMAC/Docker 설정, B 상태 구분)를 기록한다.

---

## 의존성과 실행 순서

### Phase 의존성

- **Phase 1**: 즉시 시작 가능하다.
- **Phase 2**: T002·T003을 바탕으로 수행하며 모든 사용자 스토리를 막는다.
- **US1**: Phase 2 완료 뒤 시작한다.
- **US2**: Phase 2 완료 뒤 테스트는 시작 가능하지만, terminal coordinator 구현은 US1의 Worker 경로와 함께 통합한다.
- **US3**: US1의 성공 DRAFT가 필요하고 US2의 원문 수명 보장을 함께 검증한다.
- **US4**: Phase 2의 PostgreSQL 정합성과 US1~US3 흐름이 완료된 뒤 실제 Docker 데모를 수행한다.
- **Phase 7**: 원하는 모든 사용자 스토리와 Docker smoke 뒤 수행한다.

### 사용자 스토리 완료 순서

```text
Phase 1 → Phase 2 → US1 → US2 → US3 → US4 → Phase 7
```

### 병렬 수행 예시

```text
# Phase 1: 서로 다른 테스트 파일
T002 (fresh PostgreSQL schema regression test)
T003 (no-retry execution regression test)

# US1: 서로 다른 실패 테스트를 병렬 작성
T011 (single Worker claim test)
T012 (AI response context test)
T013 (success terminal PostgreSQL test)

# US2: 실패·timeout·Audit 테스트를 병렬 작성
T019 (failure cleanup)
T020 (timeout/claim-expiry cleanup)
T021 (Audit redaction)

# US4: AI provider와 Core route 계약 테스트를 병렬 작성
T031 (Solar provider selection)
T032 (three-route HMAC client)
```

## 구현 전략

### MVP 우선

1. T001~T010으로 fresh PostgreSQL schema와 UUID/status claim 기반을 완성한다.
2. T011~T018로 `PROCESSING → Worker → AI → DRAFT → SUCCEEDED`만 먼저 독립 검증한다.
3. 이 시점에 가짜 AI client와 PostgreSQL에서 User Story 1을 시연한다.

### 점진적 인계

1. US2로 실패·timeout·중단에서도 원문이 남지 않게 한다.
2. US3로 사용자 확인과 정상 B 결과까지 연결한다.
3. US4로 Docker·Solar 실제 호출 증거를 만들고, T041로 풀스택 B 인계 여부를 판정한다.

## Notes

- `[P]`는 서로 다른 파일의 독립 작업일 때만 표기했다.
- `rank` 결과를 최종 추천에 반영하는 작업, 실제 Catalog 준비, 메시지 큐·다중 Worker·자동 재시도는 이 task list의 범위 밖이다.
- Solar key·HMAC secret은 어떤 task의 fixture·문서·커밋에도 실제 값으로 기록하지 않는다.
