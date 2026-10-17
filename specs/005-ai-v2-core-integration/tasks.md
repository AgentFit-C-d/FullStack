# 작업 목록: AI v2 핵심 로직 통합

**입력 문서**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [내부 계약](contracts/ai-service-v2-internal.yml), [quickstart.md](quickstart.md)

**원칙**: 모든 AI 호출은 DB 트랜잭션 밖에서 수행하고, Core가 저장 직전 소유권·generation·deadline·상태를 다시 검증한다. PDF·Markdown·직접 입력 원문·전체 추출문·Secret·정상 원시 공급자 응답은 terminal 상태 뒤 로그·DTO·영구 저장소에 남기지 않는다. 실제 수신한 최종 분석 실패 응답만 암호화 1건/시도·최대 7일 예외이며 Audit·일반 로그에는 남기지 않는다.

## Phase 1: 준비

**목적**: AI v2 계산 코어를 이식할 실행·테스트 기반을 만든다.

- [x] T001 AI v2 이식 의존성을 감사해 Python 3.12와 고정된 문서 추출·Upstage 의존성을 선언하고 lock file을 갱신한다 in `services/ai-service/pyproject.toml` and `services/ai-service/uv.lock`
- [x] T002 [P] AI v2 deterministic provider와 PDF·Markdown·텍스트 합성 입력 fixture를 추가한다 in `services/ai-service/tests/fixtures/ai_v2/`
- [x] T003 [P] HMAC request context·오류 envelope fixture를 추가한다 in `services/ai-service/tests/fixtures/internal_contract/`

---

## Phase 2: 공통 기반

**목적**: 모든 사용자 스토리를 막는 HMAC, 계약, Core HTTP client 확장을 먼저 완료한다.

- [x] T004 [P] 기존 HMAC canonical string의 PATH를 재사용하도록 내부 라우트 인증 helper를 일반화하고 tamper·stale·replay 거부 테스트를 작성한다 in `services/ai-service/app/internal_auth.py` and `services/ai-service/tests/test_internal_auth.py`
- [x] T005 [P] `POST /internal/v1/analysis`, `/internal/v1/skills/needs`, `/internal/v1/skills/rank`의 HMAC header, context, 400/401/503 오류와 needs의 canonical AI source-path allow-list를 공유 OpenAPI 계약에 반영한다 in `contracts/openapi/ai-service.yml`
- [x] T006 [P] 경로별 HMAC 호출과 `requestId`, generation, contractVersion, deadline 응답 대조를 지원하는 Core AI client port를 정의한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/AiAnalysisClient.java` and `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/client/AiAnalysisClientContractTest.java`
- [x] T007 Core AI HTTP client와 환경 설정을 analysis/needs/rank endpoint별로 확장하고 exact UTF-8 body SHA-256 서명 테스트를 작성한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClient.java`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/config/AiAnalysisClientConfiguration.java`, `services/core-api/src/main/resources/application.yml`, and `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClientTest.java`

**체크포인트**: 세 내부 경로가 같은 HMAC·replay 규칙과 context 검증을 사용하며, 실제 사용자 스토리 구현을 시작할 수 있다.

---

## Phase 3: User Story 1 - 승인된 문서를 분석하고 확인 뒤 B 추천 생성 (Priority: P1) 🎯 MVP

**목표**: A가 AI v2로 문서를 분석해 구조화 결과만 저장하고, 사용자 CONFIRMED snapshot 이후 B가 9개 Capability 판단과 승인 Catalog 추천을 생성한다.

**독립 검증**: synthetic PDF/Markdown/text를 분석하고 원문 없이 A 결과만 저장한다. 확인 전 추천은 거부되고, 확인 후 추천은 유효 Capability 결과로 생성된다.

### 먼저 실패해야 하는 테스트

- [x] T008 [P] [US1] PDF·Markdown·텍스트 입력 한도, 추출, evidence validation, deterministic provider 결과를 검증하는 실패 테스트를 작성한다 in `services/ai-service/tests/test_ai_v2_analysis_core.py`
- [x] T009 [P] [US1] `/internal/v1/analysis`가 유효 HMAC context를 보존하고 `CONTRACT_TEST_DOUBLE` 대신 구조화 결과를 반환하는 contract test를 작성한다 in `services/ai-service/tests/test_internal_analysis_v2.py`
- [x] T010 [P] [US1] A 분석 저장이 request context 불일치·만료에는 거부되고 유효 결과만 저장하는 테스트를 작성한다 in `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/AnalysisResultPersistenceServiceTest.java`
- [x] T011 [P] [US1] DRAFT만 있으면 HTTP 409 `PROFILE_NOT_CONFIRMED`로 추천이 거부되고, 사용자의 확인이 CONFIRMED Profile·확인 이벤트·확인 Audit을 하나의 트랜잭션에서 만들되 DRAFT를 삭제하지 않으며, 셋 중 하나가 실패하면 모두 롤백되고, 그 뒤 needs→9 Capability claim/question→Catalog 추천이 생성됨을 검증하는 테스트를 작성한다 in `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/AiCapabilityAnalyzerAdapterTest.java`, `services/core-api/src/test/java/com/agentfit/coreapi/catalog/RecommendationCreationServiceTest.java`, `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/OwnerCheckedSnapshotReaderAdapterTest.java`, `services/core-api/src/test/java/com/agentfit/coreapi/recommendation/controller/RecommendationControllerTest.java`, and `services/core-api/src/test/java/com/agentfit/coreapi/domain/audit/AuditRecorderTest.java`

### 구현

- [x] T012 [US1] AI v2의 문서 추출, 입력 한도, Solar/Information Extract provider, facts, evidence validator를 무상태 패키지로 이식하고 Bearer·Express·DB·Vercel 코드를 제외한다 in `services/ai-service/app/ai_v2/`
- [x] T013 [US1] Core가 전달한 transient document와 deadline을 분석 코어에 전달하고 구조화 분석 proposal만 반환하는 HMAC analysis route를 구현한다 in `services/ai-service/app/internal_analysis.py` and `services/ai-service/app/main.py`
- [x] T014 [US1] Core A 분석 실행 경로에서 178초 상한과 Core deadline 중 이른 시각을 사용하고 결과 저장 전 project·generation·deadline을 재검증한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisExecutionService.java` and `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/ProjectAnalysisService.java`
- [x] T015 [US1] 확인된 field만 받는 needs route를 구현하고 AI v2의 최대 6개 need를 B의 9개 고정 Capability claim (`REQUIRED|OPTIONAL|UNDETERMINED`)과 질문으로 변환한다 in `services/ai-service/app/ai_v2/needs.py` and `services/ai-service/app/main.py`
- [x] T016 [US1] Core B 어댑터가 `profile.projectName`, `profile.projectType`, `profile.domain`, `profile.item.<field_key>.<ordinal>`의 7개 허용 item key와 4개 trusted runtime path만 needs endpoint로 보내고, `purpose`, `target_users`, `constraints` 및 alias를 snapshot에 있더라도 거부하며, response context·claim/question을 검증하게 한다 in `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/AiCapabilityAnalyzerAdapter.java`, `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/ai/AiCapabilityIntake.java`, and `services/core-api/src/main/java/com/agentfit/coreapi/catalog/RecommendationCreationService.java`
- [x] T017 [US1] A 확인 서비스가 DRAFT 수정값으로 CONFIRMED Profile·불변 `profile_confirmation_event`·확인 Audit을 같은 트랜잭션에서 만들고 DRAFT를 보존하게 하며, 하나라도 실패하면 모두 롤백한다. B 추천 생성은 그 snapshot만 사용하고 미확정 상태를 HTTP 409 `PROFILE_NOT_CONFIRMED`로 매핑한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/service/ProfileConfirmationService.java`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/project/controller/ProjectController.java`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/audit/service/AuditRecorder.java`, `services/core-api/src/main/java/com/agentfit/coreapi/catalog/RecommendationCreationService.java`, `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/OwnerCheckedSnapshotReaderAdapter.java`, and `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/controller/RecommendationController.java`
- [x] T018 [US1] A 분석→사용자 확인→B 추천의 HMAC 통합 fixture를 추가하고 B의 9개 key 밖의 결과가 저장되지 않음을 검증한다 in `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/AiV2AnalysisToRecommendationIntegrationTest.java`

**체크포인트**: 문서 분석과 확인 뒤 B 추천이 독립적으로 동작하며, B public API와 승인 Catalog 규칙은 바뀌지 않는다.

---

## Phase 4: User Story 2 - 안전하지 않거나 유효하지 않은 분석 요청 거부 (Priority: P1)

**목표**: 인증·입력·deadline·공급자·계약 실패가 성공 결과나 정상 빈 추천으로 위장되지 않게 한다.

**독립 검증**: tampered, stale, replayed, malformed, timeout, provider failure 요청은 HMAC 오류 또는 별도 `FAILED` 경로로 끝나고 기존 성공 데이터는 보존된다.

### 먼저 실패해야 하는 테스트

- [x] T019 [P] [US2] 세 내부 route의 invalid HMAC, stale timestamp, replayed requestId, malformed context, expired deadline을 검증하는 FastAPI tests를 작성한다 in `services/ai-service/tests/test_internal_v2_security.py`
- [x] T020 [P] [US2] AI provider timeout·parse failure·unknown or duplicate Capability key·disallowed source field가 `FAILED`가 되고 유효 UNDETERMINED+질문만 `NEEDS_INFORMATION`이 되는 Core tests를 작성한다 in `services/core-api/src/test/java/com/agentfit/coreapi/integration/ab/AiCapabilityFailureMappingTest.java`
- [x] T021 [P] [US2] 새 추천 실패가 이전 성공 recommendation과 basis를 보존하고 `NO_COMPATIBLE_TOOLS`·`NEEDS_INFORMATION`으로 변환되지 않음을 검증하는 persistence test를 작성한다 in `services/core-api/src/test/java/com/agentfit/coreapi/recommendation/persistence/JdbcRecommendationMetadataWriterTest.java`

### 구현

- [x] T022 [US2] 세 route에 입력 크기·형식·deadline·공급자 오류 정규화와 안전한 400/401/503 envelope를 구현한다 in `services/ai-service/app/internal_analysis.py`, `services/ai-service/app/main.py`, and `services/ai-service/app/ai_v2/`
- [x] T023 [US2] Core의 AI client·analysis execution·B adapter에서 transport/contract/provider 오류를 별도 실패로 매핑하고 정상 B 상태와 분리한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClient.java`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/service/AnalysisExecutionService.java`, and `services/core-api/src/main/java/com/agentfit/coreapi/integration/ab/AiCapabilityAnalyzerAdapter.java`
- [x] T024 [US2] 기본 비활성인 failure-diagnostic 설정을 추가하고, 활성화한 환경에서만 오류 응답과 Audit에서 원문·Secret·provider raw response를 제거한 채 실제 수신한 최종 분석 실패 응답의 암호화 1건/시도·최대 7일 진단 예외를 검증한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/audit/`, `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/`, `services/core-api/src/main/resources/application.yml`, and `services/core-api/src/test/java/com/agentfit/coreapi/domain/audit/AuditRecorderTest.java`

**체크포인트**: 인증·계약·공급자 실패와 정상적인 정보 부족/호환 도구 부재가 테스트와 저장 상태에서 분리된다.

---

## Phase 5: User Story 3 - 기존 경계와 임시 원문 수명 보존 (Priority: P2)

**목표**: AI가 DB·권한·원문 장기 보관을 맡지 않고, rank는 B 담당자 결정 전 최종 추천을 바꾸지 않는다.

**독립 검증**: 성공·실패·예외 후 원문이 정리되고, rank 호출은 후보 점수만 반환하며 B recommendation row·item·status를 바꾸지 않는다.

### 먼저 실패해야 하는 테스트

- [x] T025 [P] [US3] 분석 terminal 상태(성공·공급자 실패·계약 실패·예외)에서 PDF·Markdown·직접 입력 원문과 전체 추출문이 폐기되고 A DRAFT는 남으며, 실제 수신 실패 진단만 최대 7일 예외인지 검증하는 tests를 작성한다 in `services/core-api/src/test/java/com/agentfit/coreapi/domain/analysis/TransientAnalysisInputCleanupTest.java` and `services/ai-service/tests/test_transient_input_cleanup.py`
- [x] T026 [P] [US3] rank request의 최대 6 need, 최대 15 candidate, candidate content 최대 6000자와 0..100 score를 검증하는 tests를 작성한다 in `services/ai-service/tests/test_skill_rank.py`
- [x] T027 [P] [US3] rank 응답이 recommendation item·status·persistence를 변경하지 않음을 검증하는 Core integration test를 작성한다 in `services/core-api/src/test/java/com/agentfit/coreapi/catalog/SkillRankIsolationTest.java`

### 구현

- [x] T028 [US3] analysis lifecycle의 terminal cleanup과 실패 진단 TTL을 구현하고, 기존 `document_summary`·repository·migration이 새 경로의 원문 또는 재조회 위치를 남기지 않도록 감사·최소 수정한다; CONFIRMED 생성은 DRAFT를 삭제하지 않아야 한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/`, `services/core-api/src/main/resources/db/migration/`, and `services/core-api/src/test/resources/schema-h2.sql`
- [x] T029 [US3] HMAC rank route와 AI v2 candidate scoring을 구현하되 후보 `{id,name,content}` 최대 15개와 score `0..100`만 반환하게 한다 in `services/ai-service/app/ai_v2/rank.py` and `services/ai-service/app/main.py`
- [x] T030 [US3] Core rank client를 연결하되 B 담당자 승인 전 `RecommendationCreationService`, recommendation status, recommendation item persistence가 rank 결과를 사용하지 않도록 isolation guard를 구현한다 in `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/`, `services/core-api/src/main/java/com/agentfit/coreapi/catalog/RecommendationCreationService.java`, and `services/core-api/src/test/java/com/agentfit/coreapi/catalog/SkillRankIsolationTest.java`

**체크포인트**: A/B/AI의 데이터 경계가 유지되고 rank는 미래 후보 검토 기능을 위한 내부 계산으로만 존재한다.

---

## Phase 6: 마무리 및 교차 검증

- [x] T031 [P] OpenAPI, FastAPI route, Core DTO/client fixture가 analysis/needs/rank HMAC canonical request와 error envelope에 일치하고, Core 공개 추천 계약이 HTTP 409 `PROFILE_NOT_CONFIRMED`를 명시하는지 contract tests를 정리한다 in `contracts/openapi/ai-service.yml`, `contracts/openapi/core-api.yml`, `services/ai-service/tests/`, and `services/core-api/src/test/java/com/agentfit/coreapi/contract/`
- [x] T032 [P] 문서와 환경 예시에서 AI v2 Bearer·Express·Vercel·DB 직접 접근을 제거하고 HMAC·transient input·rank 보류를 문서화한다 in `services/ai-service/README.md`, `services/core-api/src/main/resources/application.yml`, and `specs/005-ai-v2-core-integration/quickstart.md`
- [x] T033 전체 Maven·pytest·HMAC fixture·A→확인→B·rank isolation 검증을 실행하고 결과를 기록한다 in `specs/005-ai-v2-core-integration/quickstart.md`
- [x] T034 독립 코드 리뷰에서 HMAC, 원문 폐기, Core 단일 DB 소유, B 9개 Capability, 정상/실패 상태 분리를 점검한다 in `specs/005-ai-v2-core-integration/review.md`

---

## 의존성과 실행 순서

```text
Phase 1 (T001-T003)
  → Phase 2 (T004-T007)
    → US1 MVP (T008-T018)
      → US2 안전성 (T019-T024)
      → US3 경계·rank 격리 (T025-T030)
        → 마무리 (T031-T034)
```

- US1은 기반 단계가 끝난 뒤 MVP로 먼저 완료한다.
- US2와 US3의 테스트 작성은 US1 구현과 병렬 시작할 수 있지만, 공통 client·route 변경 반영은 US1 뒤에 통합한다.
- T028은 원문 수명 감사 결과가 실제 DB 변경을 요구할 때만 migration을 추가한다. raw document 저장소를 새로 만들면 안 된다.
- B 담당자가 rank 매핑·승인 규칙을 결정하기 전에는 rank의 B 최종 추천 반영 작업을 새 task로 만들지 않는다.

## 병렬 작업 예시

### US1

```text
T008: AI v2 분석 코어 테스트
T009: analysis HMAC contract test
T010: Core A 저장 보호 테스트
T011: B 확인 게이트·Capability 테스트
```

### US2

```text
T019: FastAPI 보안 테스트
T020: Core 실패 매핑 테스트
T021: 이전 성공 추천 보존 테스트
```

### US3

```text
T025: 원문 폐기 테스트
T026: rank 입력·점수 테스트
T027: rank B 격리 테스트
```

## 구현 전략

### MVP

1. T001-T007로 HMAC·계약·client 기반을 만든다.
2. T008-T018로 실제 문서 분석, 사용자 확인 게이트, B Capability/Catalog 추천을 완성한다.
3. A→확인→B 통합 fixture를 통과시킨다.

### 증분 제공

1. US1 완료 후 분석과 안전한 B 추천을 시연한다.
2. US2로 오류 상태와 이전 성공 보존을 강화한다.
3. US3로 원문 폐기와 rank 격리를 보장한다.
4. B 담당자가 별도 결정하면 rank의 최종 추천 반영을 새 기능 명세로 시작한다.
