# A-B Phase 2 작업 목록

**입력 문서**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/ab-api.md`

**구현 원칙**: 모든 핵심 업무 규칙은 실패 테스트를 먼저 작성하고 확인한 뒤 구현한다. B 서비스 인터페이스와 DTO는 변경하지 않는다.

## 1단계: 준비

- [X] T001 `services/core-api/pom.xml`과 Java 21/Maven 실행 환경을 확인하고 재현 가능한 테스트 명령을 `specs/004-a-b-integration/quickstart.md`에 고정한다.
- [X] T002 `services/core-api/src/main/resources/db/migration/`의 기존 V1~V4와 PDF A15/B9/Audit 목록을 대조하고, 기존 개발 DB 폐기·새 빈 DB 생성·전체 migration 적용 절차를 `specs/004-a-b-integration/quickstart.md`에 기록한다.
- [X] T003 [P] `contracts/openapi/core-api.yml`의 기존 오류 envelope와 프로젝트 경로 규칙을 확인해 추천/Preview의 request/response/nullable/error fixture 기준을 먼저 확정한다.

## 2단계: 기반 작업 — UUID 전체 schema

- [ ] T004 `services/core-api/src/test/resources/schema-h2.sql` 또는 PostgreSQL migration 테스트 fixture에 UUID `USER`·`PROJECT`와 A15/B9/Audit 핵심 FK를 먼저 추가해 schema 실패를 확인하고, 새 빈 DB에 전체 migration을 적용하는 실행 경계를 검증한다.
- [X] T005 `services/core-api/src/main/resources/db/migration/V5__agentfit_ab_uuid_schema.sql`에 UUID `USER`·`PROJECT` 및 A15 전체 테이블을 생성한다. PDF 기준의 `PROJECT` version, current step/review 역참조, 상태 enum, NOT NULL/nullable 규칙을 반영한다.
- [X] T006 `V5__agentfit_ab_uuid_schema.sql`에 B9 전체 테이블을 추가한다. `recommendation`, `recommendation_capability`, `recommendation_item`, `recommendation_notice`, `config_preview`, `config_selection`, `config_permission_selection`, `config_preview_file`, `config_export`의 UUID PK/FK·복합 UNIQUE·상태 CHECK를 반영한다.
- [X] T007 [P] `services/core-api/src/main/resources/db/migration/V5__agentfit_ab_uuid_schema.sql`에 공통 `AUDIT_EVENT`와 필요한 인덱스를 추가하고, 성공 업무 상태와 Audit을 project-scoped UUID로 연결한다.
- [ ] T008 `services/core-api/src/test/java/com/agentfit/coreapi/contract/UuidSchemaContractTest.java`에서 신규 개발 DB에 migration을 적용해 모든 A15/B9/Audit 테이블, UUID 타입, FK, nullable, unique/check 제약을 검증한다.
- [ ] T009 `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDeletionGraphIntegrationTest.java`에 PDF 삭제 순서의 실패 테스트를 추가한다. PROJECT lock과 활성 역참조 해제를 포함하고, 기존 데이터를 보존하지 않는 fresh DB 전제를 고정한다.
- [ ] T010 `services/core-api/src/main/java/com/agentfit/coreapi/domain/auth/`와 `domain/project/`의 repository/entity/DTO를 UUID `USER`·`PROJECT`에 맞게 조정하고, 관련 컴파일·소유권 테스트를 통과시킨다.

## 3단계: 사용자 스토리 1 — 인증된 추천(P1)

**목표**: 소유권이 확인된 A snapshot과 AI 결과를 기존 B 추천 서비스에 연결한다.

**독립 검증**: 소유 프로젝트는 추천을 생성하고, 타 프로젝트·미인증·미확정·stale basis는 B row 없이 거부한다.

- [ ] T011 [US1] `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/RecommendationSnapshotReaderTest.java`에 인증 실패, 타 프로젝트, 삭제 프로젝트, 미확정 Profile, source field allowlist 위반의 실패 테스트를 작성한다.
- [ ] T012 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/OwnerCheckedSnapshotReaderAdapter.java`에 인증 주체·project owner·confirmed Profile·review/developer/environment version을 함께 읽는 adapter를 구현한다.
- [ ] T013 [US1] `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/RecommendationInputAdapterTest.java`에 정상 AI 응답, malformed 응답, 누락 9 capability, UNDETERMINED, raw 문서/Secret 유출 방지 테스트를 작성한다.
- [ ] T014 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/AiCapabilityAnalyzerAdapter.java`에 기존 AI gateway 응답을 `AiCapabilityIntake`로 변환하고 허용 필드·상태·근거를 검증한다. 불완전 입력은 성공 추천으로 승격하지 않는다.
- [ ] T015 [US1] `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/RecommendationStoreTransactionTest.java`에 저장 직전 version 변경, Catalog 실패, Audit 실패 시 rollback 테스트를 작성한다.
- [ ] T016 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/JdbcRecommendationStoreAdapter.java`에 기존 `RecommendationCreationService.RecommendationStore`를 UUID schema와 A transaction에 연결한다.
- [ ] T017 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/RecommendationApiController.java`에 `POST /api/projects/{projectId}/recommendations`를 추가하고 SessionPrincipalResolver로 인증을 강제한다.
- [ ] T018 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/RecommendationLatestApiController.java`에 `GET /api/projects/{projectId}/recommendations/latest`를 추가하고 AI 재호출 없이 소유권·최신 basis를 검증한다.
- [ ] T019 [US1] `contracts/openapi/core-api.yml`와 `services/core-api/src/test/java/com/agentfit/coreapi/contract/RecommendationApiContractTest.java`에 확정된 request/response/nullable와 추천 성공/401/404/stale/AI failure fixture를 추가한다.

## 4단계: 사용자 스토리 2 — 신뢰된 Preview(P2)

**목표**: 소유된 최신 추천에서만 B Preview를 생성하고 metadata/fingerprint와 Audit을 원자적으로 저장한다.

**독립 검증**: 추천 소속 불일치, stale basis, Catalog hash 불일치, unsupported selection은 저장 없이 거부한다.

- [ ] T020 [US2] `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/PreviewContextReaderTest.java`에 추천 소속·project ID·basis·Environment·installed component 불일치 실패 테스트를 작성한다.
- [ ] T021 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/TrustedContextReaderAdapter.java`에 기존 `PreviewCreationService.TrustedContextReader`를 A 소유권/basis 조회와 B stored recommendation 조회에 연결한다.
- [ ] T022 [US2] `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/PreviewStoreTransactionTest.java`에 Preview metadata/fingerprint와 Audit의 원자성, 저장 실패 rollback, raw bytes/Diff 미저장 테스트를 작성한다.
- [ ] T023 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/JdbcPreviewStoreAdapter.java`에 기존 `PreviewCreationService.PreviewStore`를 UUID schema와 A transaction에 연결한다.
- [ ] T024 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/PreviewApiController.java`에 `POST /api/projects/{projectId}/config-previews`를 추가하고 추천 소속·Catalog·basis 재검증을 수행한다.
- [ ] T025 [US2] `contracts/openapi/core-api.yml`와 `services/core-api/src/test/java/com/agentfit/coreapi/contract/PreviewApiContractTest.java`에 확정된 request/response/nullable와 Preview 성공/소유권/stale/Catalog unavailable/지원되지 않는 조합 fixture를 추가한다.

## 5단계: 사용자 스토리 3 — 안전한 프로젝트 삭제(P1)

**목표**: B 종속 데이터를 기준 순서로 삭제한 뒤 A graph와 PROJECT를 같은 transaction에서 삭제한다.

**독립 검증**: B deletion 호출 순서, rollback, active analysis 차단, late result 거부를 확인한다.

- [ ] T026 [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDeletionTest.java`에 B deletion이 A PROJECT deletion보다 먼저 실행되는 실패 테스트를 작성한다.
- [ ] T027 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProjectBDeletionPort.java`를 통해 A가 B caller-owned deletion transaction을 호출하도록 연결한다.
- [ ] T028 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/configuration/persistence/JdbcProjectBDeletionPort.java`에서 기존 `JdbcProjectBDataDeleter`를 Spring outer transaction connection에 연결한다.
- [ ] T029 [US3] `services/core-api/src/test/java/com/agentfit/coreapi/domain/project/ProjectDeletionGraphIntegrationTest.java`에 명시적 B child→B Audit→A child→A Audit→PROJECT 순서, FK failure/lock conflict/Audit failure rollback과 삭제 후 late analysis/recommendation 결과 거부를 추가한다.
- [ ] T030 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProjectService.java`의 UUID owner check, PROJECT lock, active-operation check, B→B Audit→A→A Audit→PROJECT 삭제 순서와 project Audit 원자성을 완성한다.

## 6단계: 마무리·교차 검증

- [ ] T031 `services/core-api/src/test/java/com/agentfit/coreapi/contract/AccessIsolationContractTest.java`에 추천·Preview·삭제의 cross-project ID 격리 검증을 추가한다.
- [ ] T032 `contracts/openapi/core-api.yml`, `contracts/openapi/ai-service.yml`, Java DTO, 서버 validation, 정상/실패 fixture 간 필드·nullable·오류 의미를 대조한다.
- [ ] T033 `services/core-api`에서 JDK 21로 `mvn -B -ntp test`를 실행하고, PostgreSQL/Testcontainers 가능 테스트를 별도로 실행한다.
- [ ] T034 [P] `services/core-api/src/main/java/com/agentfit/coreapi/catalog`, `services/core-api/src/main/java/com/agentfit/coreapi/recommendation`, `services/core-api/src/main/java/com/agentfit/coreapi/configuration/preview` 및 대응 테스트 경로의 기존 B 서비스 interface와 DTO public signature diff를 검사하고 변경이 없음을 기록한다.
- [ ] T035 `specs/004-a-b-integration/quickstart.md`에 실제 migration 적용·API contract·rollback 검증 명령과 결과를 갱신한다.
- [ ] T036 독립 리뷰에서 보안·동시성·계약 불일치·실패 Audit 민감정보·불필요한 B 변경을 점검하고 `specs/004-a-b-integration/tasks.md`의 완료 상태를 갱신한다.
- [X] T037 `services/core-api/src/main/java/com/agentfit/coreapi/domain/audit/service/FailureAuditService.java`의 failure Audit 경로를 업무 rollback 이후 별도 최소 transaction으로 연결하고, 원문·Secret·raw AI 응답을 제외한다.
- [X] T038 `services/core-api/src/test/java/com/agentfit/coreapi/contract/FailureAuditSafetyTest.java`에 인증·소유권/참조 불일치·삭제 프로젝트·stale/version·미확정/UNDETERMINED·AI 응답 검증·Catalog·활성 삭제 거부·FK/lock 실패의 최소 Audit 기록, business data rollback, 민감정보 미저장 검증을 추가한다.

## 의존성 및 실행 순서

```text
T001-T003
   ↓
T004-T010  UUID schema와 A repository 기반
   ↓
T011-T019  US1 추천
   ↓
T026-T030  US3 삭제 transaction
   ↓
T020-T025  US2 Preview
   ↓
T031-T038  전체 계약·회귀·리뷰
```

- T003은 T001/T002와 독립적으로 수행할 수 있다.
- T011/T013/T015는 각각 다른 adapter 테스트 파일이므로 T010 이후 병렬 수행 가능하다.
- T020/T022는 T019 이후 병렬 수행 가능하다.
- US2는 저장된 recommendation schema와 owner snapshot이 필요하므로 US1 이후 실행한다.
- 삭제는 모든 B child table과 UUID PROJECT schema가 필요하므로 기반 작업 이후 실행하며, P1 데이터 무결성을 우선해 Preview보다 먼저 검증한다.
- T037/T038은 T015/T022/T029의 실패 rollback 규칙이 확정된 뒤 수행한다.

## MVP 범위

1. T001-T010: UUID A15+B9+Audit schema
2. T011-T019: 인증된 추천 생성/최신 조회
3. T026-T030: 프로젝트 삭제 transaction
4. T037-T038: 실패·거부 Audit 안전 경로

Preview는 추천 저장과 snapshot 계약이 검증된 뒤 T020-T025로 추가한다. 각 단계는 focused test가 통과한 뒤 다음 단계로 진행한다.

