# Tasks: 프로젝트 생성과 분석 시작 MVP

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [API 계약](contracts/project-analysis-mvp.md), [quickstart.md](quickstart.md)

**테스트**: 명세의 FR-014와 SC-006~SC-009가 기본 자동 테스트를 요구하므로, 각 사용자 스토리에서 실패 테스트를 먼저 작성한다.

**조직 방식**: 작업은 사용자 스토리별로 묶는다. `[P]`는 이전 의존 작업이 완료된 뒤 다른 파일에서 병렬로 수행할 수 있는 작업이다.

## Phase 1: Setup (공통 준비)

**목적**: 파일 입력 검증과 PostgreSQL 통합 테스트를 실행할 최소 의존성·설정을 준비한다.

- [X] T001 의존성 추가: `services/core-api/pom.xml`에 PDF 페이지/텍스트 한도 검증용 Apache PDFBox와 PostgreSQL 동시성 통합 테스트용 Testcontainers PostgreSQL test dependency를 추가한다.
- [X] T002 [P] `services/core-api/src/main/java/com/agentfit/coreapi/CoreApiApplication.java`와 `services/core-api/src/main/java/com/agentfit/coreapi/global/config/TimeConfiguration.java`에 scheduling 활성화 및 테스트 가능한 UTC `Clock` bean을 추가한다.
- [X] T003 [P] `services/core-api/src/main/resources/application.yml`에 600초 분석 기한과 최대 10초 만료 정리 주기를 환경 변수로 재정의 가능한 `agentfit.analysis` 설정으로 추가한다.

---

## Phase 2: Foundational (모든 스토리를 막는 기반)

**목적**: 데이터 저장, 오류 계약, 안전한 Audit, OpenAPI 기반을 마련한다. 이 단계가 끝나기 전에는 사용자 스토리 구현을 시작하지 않는다.

- [X] T004 `services/core-api/src/main/resources/db/migration/V4__project_analysis_mvp.sql`에 `PROJECT.developer_role`, `current_input_revision`, `PROJECT_RUNTIME`, `PLANNING_INPUT`, `PROJECT_CREATION_IDEMPOTENCY`, `ANALYSIS_ADMISSION`을 추가하고, 기존 `ANALYSIS_ATTEMPT`에 owner/input revision/error code를 확장한다. runtime은 `(project_id, normalized_value)`로 유일해야 하고, Attempt는 프로젝트·사용자별 `PROCESSING` partial unique index를 가져야 한다.
- [X] T005 [P] `services/core-api/src/main/java/com/agentfit/coreapi/global/exception/ErrorCode.java`, `GlobalExceptionHandler.java`, `domain/project/exception/AnalysisAlreadyProcessingException.java`, `AnalysisCapacityExceededException.java`, `IdempotencyKeyReusedException.java`, `ActiveAnalysisProjectDeletionException.java`에 `409 ANALYSIS_ALREADY_PROCESSING`, `409 IDEMPOTENCY_KEY_REUSED`, `409 ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN`, `429 ANALYSIS_USER_LIMIT_REACHED`, `429 ANALYSIS_SERVER_LIMIT_REACHED` 오류 봉투를 추가한다.
- [X] T006 [P] `services/core-api/src/main/java/com/agentfit/coreapi/domain/audit/service/AuditRecorder.java`와 `services/core-api/src/test/java/com/agentfit/coreapi/domain/audit/AuditRecorderTest.java`에서 `raw_content`, `inputText`, `document`, `extracted_text`, `citation` 및 변형 키가 Audit metadata에 남지 않도록 차단 테스트를 먼저 추가하고 구현한다.
- [X] T007 [P] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/entity/DeveloperRole.java`, `PlanningInput.java`, `ProjectRuntime.java`, `ProjectCreationIdempotency.java`와 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisAdmission.java`에 데이터 모델의 값 객체를 추가한다.
- [X] T008 `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/repository/ProjectRepository.java`, `JdbcProjectRepository.java`, `ProjectRuntimeRepository.java`, `JdbcProjectRuntimeRepository.java`, `PlanningInputRepository.java`, `JdbcPlanningInputRepository.java`, `ProjectCreationIdempotencyRepository.java`, `JdbcProjectCreationIdempotencyRepository.java`, `domain/analysis/repository/AnalysisAdmissionRepository.java`, `JdbcAnalysisAdmissionRepository.java`에 V4 행 저장·조회·`FOR UPDATE`·raw scrub 연산을 추가한다.
- [X] T009 `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/repository/AnalysisAttemptRepository.java`와 `JdbcAnalysisAttemptRepository.java`에 프로젝트/사용자 활성 Attempt 조회, `PROCESSING` 조건부 terminal 전환, 입력 revision/오류 코드 저장을 추가한다.
- [X] T010 `contracts/openapi/core-api.yml`을 0.2.0으로 올려 기존 JSON 생성에서 multipart 생성으로의 의도된 breaking change와 실제 세션 쿠키 `Path=/` 설명을 기록하고, 프로젝트 생성·수정·입력 교체·분석 시작·활성 분석 중 삭제 거부 endpoint, `Idempotency-Key`, 409/429 오류와 안전한 detail schemas를 계약 문서와 일치시킨다.

**Checkpoint**: Flyway V4, 오류 코드, Repository 경계, OpenAPI가 준비되어 모든 사용자 스토리를 구현할 수 있다.

---

## Phase 3: User Story 1 — 프로젝트 정보와 기획 입력 저장 (Priority: P1) 🎯 MVP

**목표**: 로그인한 사용자가 이름·단일 역할·중복 없는 runtime·PDF/Markdown 파일 또는 직접 일반 텍스트 하나로 프로젝트를 생성한다.

**독립 검증**: 유효한 multipart 요청으로 프로젝트를 생성한 뒤 생성 응답에서 이름·role·runtime·입력 메타데이터를 확인한다. 원문·ownerId는 응답에 없어야 한다.

### Tests for User Story 1

- [X] T011 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectCreationWithInputTest.java`에 프로젝트 이름 1~100 Unicode code points, role enum 5종, runtime 줄바꿈/쉼표 분리·빈 값 제거·대소문자 무시 중복 제거·최대 30개·항목당 200 code points, 생성 원자성의 실패 테스트를 작성한다.
- [X] T012 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/PlanningInputValidationTest.java`에 PDF/Markdown 파일 최대 10 MiB(10,485,760 bytes), PDF 최대 100쪽, 추출문/직접 UTF-8 일반 텍스트 최대 100,000 Unicode code points, 파일과 `inputText` 정확히 하나 규칙의 실패 테스트를 작성한다.
- [X] T013 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectMultipartWebMvcTest.java`에 `POST /api/projects` multipart의 인증·CSRF·`Idempotency-Key`·201 응답과 raw/ownerId 비노출 계약 테스트를 작성한다.
- [X] T014 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectCreationIdempotencyTest.java`와 `ProjectCreationIdempotencyPostgresIntegrationTest.java`에 같은 사용자·같은 키·같은 입력의 순차·동시 재시도는 하나의 프로젝트와 동일 응답을 재사용하고, 같은 키·다른 fingerprint는 `409 IDEMPOTENCY_KEY_REUSED`가 되는 실패 테스트를 작성한다.

### Implementation for User Story 1

- [X] T015 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/dto/request/ProjectCreateMetadataRequest.java`, `ProjectCreateRequestParts.java`, `dto/response/ProjectProfileResponse.java`, `PlanningInputMetadataResponse.java`, `ProjectDetailResponse.java`에 multipart 생성 입력과 원문을 제외한 프로젝트 상세 응답 DTO를 정의한다.
- [X] T016 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/PlanningInputValidator.java`에 파일 MIME/확장자, 10 MiB, PDFBox 기반 100쪽·100,000 code point, UTF-8 `text/plain` 직접 입력 100,000 code point 검증을 구현한다.
- [X] T017 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProjectService.java`에 멱등성 키 예약·fingerprint 비교, 프로젝트/role/runtime/입력 메타데이터와 원문 저장, `PROJECT_CREATED` 최소 Audit을 하나의 트랜잭션으로 구현한다.
- [X] T018 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/controller/ProjectController.java`에 `POST /api/projects` multipart endpoint를 추가하고 `Idempotency-Key`·CSRF·세션 principal을 서비스 호출로 연결한다.
- [X] T019 [US1] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectCreationWithInputTest.java`, `PlanningInputValidationTest.java`, `ProjectMultipartWebMvcTest.java`, `ProjectCreationIdempotencyTest.java`, `ProjectCreationIdempotencyPostgresIntegrationTest.java`를 실행해 T011~T014의 실패 테스트를 통과시킨다.

**Checkpoint**: 로그인 사용자는 한 종류의 유효한 기획 입력과 프로젝트 정보를 저장할 수 있고, 재시도는 중복 프로젝트를 만들지 않는다.

---

## Phase 4: User Story 2 — 저장된 프로젝트 정보 복원과 수정 (Priority: P1)

**목표**: 소유자가 새로고침 뒤에도 프로젝트 메타데이터를 재조회하고 이름·role·runtime을 version 안전하게 수정하며, 필요하면 새 기획 입력 revision을 제출한다.

**독립 검증**: 생성 뒤 `GET`으로 안전한 메타데이터를 조회하고, `PATCH` 후 새 version을 재조회한다. 오래된 version은 409이며, 종료된 입력 원문은 응답에 없다.

### Tests for User Story 2

- [X] T020 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDetailAndUpdateTest.java`에 소유자 상세 조합, 최신 runtime 순서, 입력 메타데이터 재조회, `expectedVersion` 불일치 `409 VERSION_CONFLICT`, 업데이트 뒤 version 증가 테스트를 작성한다.
- [X] T021 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/PlanningInputRevisionTest.java`에 활성 Attempt가 없을 때만 새 입력 revision을 만들고 이전 revision의 메타데이터만 보존하는 테스트를 작성한다.
- [X] T022 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDetailWebMvcTest.java`에 `GET`, `PATCH`, `PUT /planning-input`의 200/409/404와 raw/추출문/ownerId 비노출 계약 테스트를 작성한다.

### Implementation for User Story 2

- [X] T023 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/dto/request/ProjectUpdateRequest.java`와 `PlanningInputUpdateMetadataRequest.java`에 `expectedVersion`을 포함한 수정 DTO를 추가한다.
- [X] T024 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProjectService.java`와 `domain/project/repository/ProjectRepository.java`, `JdbcProjectRepository.java`에 소유자 상세 조합, project 행 잠금, version 확인·증가, 이름·role·runtime 수정, 입력 revision 교체와 최소 Audit을 구현한다.
- [X] T025 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/controller/ProjectController.java`에 `PATCH /api/projects/{projectId}`와 `PUT /api/projects/{projectId}/planning-input`을 추가하고 기존 `GET` 상세 응답을 안전한 profile/input metadata로 채운다.
- [X] T026 [US2] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDetailAndUpdateTest.java`, `PlanningInputRevisionTest.java`, `ProjectDetailWebMvcTest.java`를 실행해 T020~T022의 실패 테스트를 통과시킨다.

**Checkpoint**: 프로젝트 정보 화면은 서버에 남은 이름·role·runtime·입력 메타데이터를 복원하며, 수정 충돌이나 원문 유출 없이 변경된다.

---

## Phase 5: User Story 4 — 인증과 기본 입력 검증 (Priority: P1)

**목표**: 비로그인·타인·잘못된 입력 요청은 프로젝트나 분석 상태를 바꾸지 못한다.

**독립 검증**: 인증되지 않은 생성/수정/입력 교체/분석 시작과 타인 프로젝트 요청이 401 또는 404를 반환하고 DB 상태가 변하지 않는다.

### Tests for User Story 4

- [X] T027 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectInputSecurityWebMvcTest.java`에 세션 없는 생성·수정·입력 교체 요청의 401, CSRF 누락 요청의 403, 타인 프로젝트 상세·수정·입력 교체의 404 테스트를 작성한다.
- [X] T028 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectInputBoundaryWebMvcTest.java`에 빈/공백·100 code point 초과 이름, 허용하지 않는 role, 31개 runtime, 201 code point runtime, 입력 없음/둘 다 입력의 422 및 부분 저장 없음 테스트를 작성한다.

### Implementation for User Story 4

- [X] T029 [US4] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/controller/ProjectController.java`, `domain/project/service/ProjectService.java`, `global/exception/GlobalExceptionHandler.java`에 T027~T028에서 고정한 인증·소유권·검증 오류 경로를 연결하고 생성/수정/입력 교체가 실패 시 전체 롤백되게 한다.
- [X] T030 [US4] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectInputSecurityWebMvcTest.java`와 `ProjectInputBoundaryWebMvcTest.java`를 실행해 T027~T028의 실패 테스트를 통과시킨다.

**Checkpoint**: 입력·소유권·인증 경계가 프로젝트 변경 이전에 적용된다.

---

## Phase 6: User Story 3 — 분석 시작과 상태 표시 (Priority: P2)

**목표**: 저장된 입력을 가진 소유자가 분석을 `PROCESSING`으로 접수하고, 프로젝트/사용자/서버 제한과 600초 timeout이 안전하게 적용된다.

**독립 검증**: 유효한 시작 요청은 `202 PROCESSING`을 반환한다. 같은 프로젝트는 409, 같은 사용자 또는 세 번째 서버 활성 요청은 429이며, 만료 뒤 원문은 삭제되고 슬롯은 회수된다.

### Tests for User Story 3

- [X] T031 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/ProjectAnalysisServiceTest.java`에 입력 없는 프로젝트 거부, `expectedVersion` 확인, 성공 시 600초 deadline·`PROCESSING`, 같은 프로젝트 `409`, 사용자 1건·서버 2건 `429`의 실패 테스트를 작성한다.
- [X] T032 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisTimeoutCleanupTest.java`에 최대 10초 간격의 주입된 Clock 기반 scanner가 600초 만료 Attempt를 `FAILED/TIMEOUT`으로 한 번만 전환하고 raw/추출문 삭제·admission 감소·최소 Audit을 검증하는 실패 테스트를 작성한다.
- [X] T033 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisAdmissionPostgresIntegrationTest.java`에 PostgreSQL Testcontainers에서 동시 시작 요청 세 건이 최대 두 건의 `PROCESSING`만 만들고 partial unique index/admission lock을 지키는 테스트를 작성한다.
- [X] T034 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/ProjectAnalysisWebMvcTest.java`에 `POST /api/projects/{projectId}/analysis-attempts`의 202 상세 응답, 같은 프로젝트 409 지정 안내 문구, 사용자/서버 429 오류 봉투 계약 테스트를 작성한다.

### Implementation for User Story 3

- [X] T035 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/entity/AnalysisAttempt.java`, `domain/analysis/repository/AnalysisAttemptRepository.java`, `JdbcAnalysisAttemptRepository.java`, `AnalysisAdmissionRepository.java`, `JdbcAnalysisAdmissionRepository.java`에 input revision·owner·오류 코드·active count와 조건부 terminal SQL을 구현한다.
- [X] T036 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`에 `PROJECT` → `ANALYSIS_ADMISSION` 잠금 순서, ownership/입력/version 검사, 409/429 판정, Attempt 생성, generation/version 갱신과 최소 Audit을 하나의 트랜잭션으로 구현한다.
- [X] T037 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`와 `AnalysisTimeoutScheduler.java`에 후속 성공·실패·취소 경로도 재사용할 terminal cleanup 연산, 최대 10초 간격 만료 후보 조회, `PROCESSING` 조건부 `FAILED/TIMEOUT` 전환, raw scrub, admission 회수, 중복 실행 안전성을 구현한다.
- [X] T038 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/controller/ProjectController.java`와 `domain/project/dto/request/AnalysisStartRequest.java`, `dto/response/AnalysisAttemptResponse.java`에 분석 시작 endpoint와 안전한 Attempt 응답을 추가한다.
- [X] T039 [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/ProjectAnalysisServiceTest.java`, `AnalysisTimeoutCleanupTest.java`, `AnalysisAdmissionPostgresIntegrationTest.java`, `ProjectAnalysisWebMvcTest.java`를 실행해 T031~T034의 실패 테스트를 통과시킨다.

**Checkpoint**: 분석 결과 생성 없이도 접수·상태·동시성·만료·원문 삭제가 서버에서 일관되게 동작한다.

---

## Phase 7: Polish & Cross-Cutting Concerns

**목적**: 계약과 문서, 전체 회귀 검증, 독립 리뷰 준비를 마무리한다.

- [X] T040 [P] `contracts/openapi/core-api.yml`과 `services/core-api/src/test/java/com/agentfit/coreapi/contract/ProjectAnalysisContractTest.java`에 OpenAPI schema와 public DTO·오류 코드·원문 비노출 계약의 정합성 테스트를 추가한다.
- [X] T041 [P] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProjectService.java`, `controller/ProjectController.java`, `contracts/openapi/core-api.yml`, `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDeletionGraphIntegrationTest.java`에 `PROCESSING` Attempt가 있는 프로젝트 삭제를 `409 ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN`으로 거부하고 Attempt·원문·admission count가 변하지 않는 테스트를 추가한다. terminal 상태 프로젝트는 runtime, 입력 메타데이터/원문, Attempt, 멱등성 레코드가 함께 제거되는 기존 cascade를 유지한다.
- [X] T042 `specs/003-project-analysis-mvp/quickstart.md`의 명령과 수동 시나리오(활성 분석 중 삭제 409, 동일 멱등성 키 동시 재시도 포함)대로 `services/core-api`에서 Maven 전체 테스트를 실행하고, Docker 미실행으로 Testcontainers가 불가하면 그 사실과 미실행 범위를 결과에 기록한다.
- [X] T043 독립 리뷰 기록: `specs/003-project-analysis-mvp/review.md`에 원자성, 원문 비보관, 소유권, 버전 충돌, 409/429 구분, PostgreSQL 동시성, OpenAPI/DTO/테스트 정합성을 독립 리뷰 체크 결과로 기록한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 즉시 시작 가능
- **Phase 2**: Phase 1 뒤 시작하며 모든 스토리를 막음
- **US1**: Phase 2 완료 뒤 시작
- **US2**: US1의 생성·입력 저장 구현 뒤 시작
- **US4**: Phase 2 완료 뒤 시작 가능하지만, 실제 endpoint 변경 충돌을 피하려면 US1과 함께 순차 적용
- **US3**: US1의 입력 저장과 Phase 2의 Attempt/admission 저장소 뒤 시작
- **Polish**: 원하는 모든 스토리 완료 뒤 실행

### User Story Dependencies

```text
Setup → Foundational → US1 ──┬──→ US2
                             ├──→ US4
                             └──→ US3 → Polish
```

- **US1 (P1)**: 독립 MVP. 생성·입력 저장·멱등성을 제공한다.
- **US2 (P1)**: US1이 만든 프로젝트·입력 메타데이터를 조회·수정한다.
- **US4 (P1)**: 기존 인증 기반 위에서 US1/US2 endpoint의 거부 경계를 고정한다.
- **US3 (P2)**: US1의 현재 입력을 사용해 분석 Attempt를 만든다.

### Parallel Opportunities

- Phase 1의 T002/T003, Phase 2의 T005/T006/T007은 서로 다른 파일이므로 병렬 가능하다.
- US1 테스트 T011~T014, US2 테스트 T020~T022, US4 테스트 T027~T028, US3 테스트 T031~T034는 각 묶음 안에서 병렬 가능하다.
- Foundational 완료 뒤 US4의 테스트 작성은 US1 서비스 구현과 병렬 가능하지만, 둘 다 `ProjectController`를 수정하는 구현 단계는 순차 적용한다.

## Parallel Example: User Story 3

```text
병렬 실행 가능:
- T031 ProjectAnalysisService 단위 테스트
- T032 timeout/raw scrub 단위 테스트
- T033 PostgreSQL admission 통합 테스트
- T034 분석 시작 MockMvc 계약 테스트

순차 의존:
T035 저장소 확장 → T036 분석 접수 서비스 → T037 timeout scheduler/T038 controller → T039 전체 테스트
```

## Implementation Strategy

### MVP First

1. T001~T010으로 기반을 완료한다.
2. T011~T019로 US1을 완성한다.
3. 생성/입력 검증/멱등성을 독립 검증한다.

### Incremental Delivery

1. US1: 생성과 입력 저장
2. US2: 새로고침용 상세 재조회와 수정
3. US4: 인증·소유권·경계 입력 거부
4. US3: 분석 접수·동시성·만료 정리
5. Polish: 계약·삭제 cascade·전체 회귀·독립 리뷰

## Format Validation

- 모든 작업은 `- [ ] T### [P?] [US?] 설명과 정확한 파일 경로` 형식을 따른다.
- 사용자 스토리 작업은 모두 `[US1]`~`[US4]` 표기를 포함한다.
- 테스트 작업은 구현 작업보다 앞에 배치했다.
