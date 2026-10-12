---
description: "프로젝트 소유권과 접근 격리 구현 작업 목록"
---

# Tasks: 프로젝트 소유권과 접근 격리

**Input**: `specs/002-project-ownership/`의 `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**범위**: GitHub OAuth·서버 세션·CSRF, 개인 소유 프로젝트의 인증 주체 연결, 소유권·하위 리소스 격리, 버전 충돌, Audit, 삭제와 지연 결과 차단, HMAC 기반 Core API–AI Service 내부 요청 검증

**현재 상태**: Core API에는 `/health`만 구현되어 있고 프로젝트·인증·DB·Audit 기능은 미구현이다. 이 작업 목록은 구현 순서를 정의하며, 문서에만 있는 API 경로·DTO를 구현 완료로 간주하지 않는다.

**테스트 원칙**: 핵심 업무 규칙은 실패하는 테스트를 먼저 작성하고 구현 후 통과시킨다. Maven이 현재 PATH에 없으므로 실제 실행 전 Maven 설치 또는 경로 설정을 확인한다.

## Phase 1: Setup 및 계약 승인 게이트

**목적**: 구현에 들어가기 전에 미결정 계약을 확정하고 기존 프로젝트 구조를 보존한다.

- [x] T001 `specs/002-project-ownership/contracts/project-ownership.md`에 OAuth 시작·callback, CSRF·logout, 프로젝트 생성·목록·상세·삭제, HTTP method·request/response DTO·nullable·이름 길이·삭제 확인 wire 형식·이후 보호 쓰기의 충돌 오류와 A6 결과 저장 fixture를 확정해 기록한다. 프로젝트 자체 수정 endpoint는 제외한다.
- [x] T002 `contracts/openapi/core-api.yml`에 T001에서 승인한 OAuth·CSRF·logout·세션과 프로젝트 생성·목록·상세·삭제 계약, 401/403/404/422 오류 schema를 반영하고 `specs/002-project-ownership/contracts/project-ownership.md`의 fixture와 버전을 맞춘다. 409는 실제 프로젝트 수정 endpoint가 아닌 이후 보호 쓰기 계약에서만 사용한다.
- [x] T003 `contracts/openapi/ai-service.yml`과 `specs/002-project-ownership/contracts/project-ownership.md`에 `POST /internal/v1/analysis`, `X-AgentFit-Key-Id`·`X-AgentFit-Timestamp`·`X-AgentFit-Signature`, canonical 문자열, ±5분 timestamp, key 교체, 10분 중복 억제, `401 INTERNAL_AUTH_FAILED`, FastAPI의 DB·Spring Session JDBC 테이블 미접근을 확정한다.
- [x] T004 `specs/002-project-ownership/research.md`와 `specs/002-project-ownership/plan.md`에 T001~T003의 결정·담당자·승인일을 남기고, OAuth 성공 `/?login=success`·실패/취소 `/?login=failed`, HMAC key 환경 변수 규칙, `CONTRACT_TEST_DOUBLE`을 확정한다. 인증 저장 구조는 기존 USER의 `github_user_id BIGINT NULL`·`github_login VARCHAR(255) NULL`, null이 아닌 GitHub ID 유일성, 자동 계정 연결 금지, Spring Session JDBC 기본 테이블, `AGENTFIT_SESSION` cookie, `services/core-api/src/main/resources/db/migration/`의 Flyway versioned SQL로 확정하고, 프로젝트·하위 업무 데이터 DDL 세부와 실제 LLM 업무 규칙만 후속 결정으로 기록한다.
- [x] T005 `services/core-api/pom.xml`의 기존 Spring Boot 3.3.5·Java 21·Web·Validation·Test 구성을 확인하고 Spring Security OAuth2 Client·PostgreSQL 기반 Spring Session JDBC·Flyway·승인된 JPA/JDBC·PostgreSQL 의존성만 최소 범위로 추가한다.

---

## Phase 2: Foundational (모든 스토리를 막는 기반)

**목적**: 사용자 스토리 구현 전에 인증 주체, 공통 오류, 논리 모델의 물리화, 트랜잭션·잠금 기반을 준비한다.

**⚠️ 중요**: 이 단계가 끝나기 전에는 사용자 스토리 기능 구현을 시작하지 않는다.

- [x] T006 `services/core-api/src/test/java/com/agentfit/coreapi/auth/SessionPrincipalResolverTest.java`와 `OAuthLoginSecurityTest.java`에 구현보다 먼저 `/api/oauth2/authorization/github`·`/api/login/oauth2/code/github` OAuth 성공·취소·실패와 고정 redirect(`/?login=success`, `/?login=failed`)를 검증한다. callback은 `github_user_id`로만 USER를 조회하고, 불일치 시 `github_user_id BIGINT`·`github_login VARCHAR(255)`가 채워진 USER를 생성하며, 이메일·이름·`github_login` 자동 연결과 provider token 저장은 하지 않는 테스트를 포함한다. 성공에는 이름이 `AGENTFIT_SESSION`인 세션 cookie를 발급하고 실패·취소에는 발급하지 않으며, Location은 provider `error`·`code`·`state`·token을 포함하지 않는다. `GET /api/auth/get-session`은 유효한 Spring Session JDBC 세션이면 `user.id`·`user.name`·`session.expiresAt`만 포함한 200을, 쿠키 없음·없는/만료/폐기 서버 세션이면 오류가 아닌 `200 null`을 반환하며 token·Account·원본 Spring Session 레코드를 노출하지 않는 테스트를 먼저 작성한다. `AGENTFIT_SESSION`은 host-only·`Path=/api`·운영 환경 `Secure`·`SameSite=Lax`·`Max-Age=7일` 지속 HttpOnly이고, 7일 비활성 만료·쿠키 자동 연장 없음, `Path=/`·비-HttpOnly·세션성·운영 환경 `Secure`·`SameSite=Lax` `XSRF-TOKEN`, 누락·불일치 `X-XSRF-TOKEN`의 403, CORS credential 미사용, `POST /api/auth/logout`, `PRINCIPAL_NAME` 내부 userId 연결 실패를 먼저 실패시킨다.
- [x] T007 [P] `services/core-api/src/test/java/com/agentfit/coreapi/error/ApiExceptionHandlerTest.java`에 비로그인 401, 타인·없는 프로젝트의 동일 404, 입력 오류, 버전 충돌 응답 테스트를 구현보다 먼저 작성한다.
- [x] T008 [P] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectLockingPolicyTest.java`에 프로젝트 우선 잠금·기준 버전 확인 순서와 충돌 시 저장 금지 테스트를 구현보다 먼저 작성한다.
- [x] T009 [P] `services/core-api/src/test/java/com/agentfit/coreapi/audit/AuditRecorderTest.java`에 성공 Audit의 최소 필드와 secret·서명·원시 AI 응답 미저장 테스트를 구현보다 먼저 작성한다.
- [x] T010 `services/core-api/src/main/java/com/agentfit/coreapi/auth/SecurityConfig.java`, `GitHubOAuth2UserService.java`, `AuthenticatedPrincipal.java`, `AuthController.java`, `SessionPrincipalResolver.java`에 T006을 통과시키는 OAuth 경로·token 교환 뒤 provider token 폐기·`github_user_id` 전용 USER 조회/생성·PostgreSQL Spring Session JDBC의 `SPRING_SESSION` `PRINCIPAL_NAME` 기반 `userId` 인증 주체·CSRF 발급/검증·logout·`AGENTFIT_SESSION` cookie 정책을 구현한다. 이메일·이름·`github_login` 자동 연결, `OAUTH_ACCOUNT`·별도 AgentFit `SESSION` 테이블, provider token 저장을 추가하지 않는다. `GET /api/auth/get-session`은 유효 세션에 PublicSession만 반환하고, 미로그인·없는/만료/폐기 서버 세션에는 `200 null`을 반환하도록 구현한다.
- [x] T011 `services/core-api/src/main/java/com/agentfit/coreapi/error/ApiExceptionHandler.java`와 관련 오류 타입에 T007의 401, 403 `CSRF_INVALID`, 동일한 404 `PROJECT_NOT_FOUND`, 입력 거부, 이후 보호 쓰기의 버전 충돌 공통 응답 의미를 구현한다.
- [x] T012 `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectLockingPolicy.java`에 T008의 모든 프로젝트 쓰기 `PROJECT` 선행 잠금과 기준 버전 확인 순서를 구현한다.
- [x] T013 `services/core-api/src/main/resources/db/migration/`에 기존 Flyway 이력의 다음 버전 번호로 USER에 `github_user_id BIGINT NULL`·`github_login VARCHAR(255) NULL`을 추가하고 `github_user_id IS NOT NULL` partial unique index 또는 동등한 제약을 적용하는 **인증 전용** additive SQL migration을 작성한다. 기존 null 값은 보존하고 자동 backfill·자동 계정 연결은 하지 않는다. Spring Session JDBC의 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` schema를 적용하며, `OAUTH_ACCOUNT`·별도 AgentFit `SESSION`·`VERIFICATION`은 만들지 않는다. `PROJECT`·`AUDIT`·`ANALYSIS_ATTEMPT` 등 업무 데이터 migration은 각 업무 schema가 정해지는 후속 작업에서 별도로 작성한다.
- [x] T014 `services/core-api/src/main/java/com/agentfit/coreapi/audit/AuditRecorder.java`, `AuditEvent.java`에 T009의 행위자·프로젝트·대상·시점·결과·사유 최소 Audit과 민감정보 제외 규칙을 구현한다. `AUDIT`의 Flyway migration은 `PROJECT` 참조와 함께 US1의 T020에서 작성한다.
- [x] T015 [P] `services/core-api/src/test/java/com/agentfit/coreapi/analysis/HmacKeyRingTest.java`에 현재 keyId·secret 쌍 누락 시 시작 실패, 이전 keyId·secret·validUntil 셋 중 하나만 있으면 시작 실패, 만료된 이전 key 거부, secret·서명·raw body 로그/Audit 미기록 테스트를 구현보다 먼저 작성한다.
- [x] T016 `services/core-api/src/main/java/com/agentfit/coreapi/analysis/HmacKeyRing.java`와 설정 바인딩에 `AGENTFIT_HMAC_CURRENT_KEY_ID`·`AGENTFIT_HMAC_CURRENT_SECRET`의 필수 쌍, 선택 이전 keyId·secret·validUntil 셋, 만료 거부·시작 실패 규칙을 구현해 T015를 통과시킨다.

**Checkpoint**: 고정 테스트 주체와 PostgreSQL 테스트 DB에서 인증·오류·잠금·Audit 기반을 독립적으로 검증할 수 있어야 한다.

---

## Phase 3: User Story 1 - 본인 프로젝트만 시작하고 찾기 (Priority: P1) 🎯 MVP

**목표**: GitHub로 식별된 인증 주체가 자신의 프로젝트만 생성·목록 조회·상세 조회한다.

**독립 테스트**: 서로 다른 두 인증 주체로 프로젝트를 생성한 뒤 각자의 목록·상세에 상대 프로젝트가 없고 공개 결과에 `ownerId`가 없는지 확인한다.

### User Story 1 테스트 (구현 전에 실패해야 함)

- [x] T017 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/contract/ProjectOwnershipContractTest.java`에 `owner-project-read` fixture의 공개 필드·`ownerId` 제외 검증을 작성한다.
- [x] T018 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectCreationTest.java`에 인증 주체 기반 소유자 결정, ownerId 위조 무시·거부, 빈 문자열·공백 이름 거부 테스트를 작성한다.
- [x] T019 [P] [US1] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectQueryTest.java`에 사용자별 목록 격리와 자신의 상세 조회 테스트를 작성한다.

### User Story 1 구현

- [x] T020 [P] [US1] `services/core-api/src/main/java/com/agentfit/coreapi/project/Project.java`, `src/main/resources/db/migration/`에 소유자 참조, 불투명 ID, 이름, 공통 버전, 현재 단계, 생성·갱신 시점, 분석·Review 세대를 모델링하고, 이 US1에 필요한 확정 `PROJECT` 필드·소유자 참조·버전 제약 및 `PROJECT` 참조를 사용하는 최소 `AUDIT` 테이블을 담는 Flyway migration을 작성한다.
- [x] T021 [P] [US1] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectRepository.java`에 인증 주체 기준 생성·목록·상세 조회와 프로젝트 존재·소유권 조회를 구현한다.
- [ ] T022 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectService.java`에 서버 인증 주체로만 소유자를 정하는 생성·목록·상세 업무를 구현하고 성공 변경 시 버전·Audit 트랜잭션 경계를 연결한다.
- [x] T023 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectController.java`에 T001~T002에서 승인한 실제 경로·DTO로 생성·목록·상세 endpoint를 구현한다.
- [x] T024 [US1] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectResponse.java`와 `ProjectCreateRequest.java`에 공개 응답의 `ownerId` 제외와 허용된 사용자 입력만 받는 DTO를 구현한다.
- [x] T025 [US1] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectWebMvcTest.java`에 정상 생성·목록·상세, 빈 이름, ownerId/서버 상태 주입 거부의 MockMvc 검증을 추가한다.

**Checkpoint**: US1만 배포해도 두 사용자의 프로젝트 생성·목록·상세가 서로 격리되어야 한다.

---

## Phase 4: User Story 2 - 우회 요청에서도 타인 프로젝트를 보지 못하기 (Priority: P1)

**목표**: 화면을 우회한 직접 API 요청과 하위 리소스 ID 조합에서도 타인 데이터와 존재 여부를 노출하지 않는다.

**독립 테스트**: 비로그인, 비소유 사용자, 존재하지 않는 ID, 다른 프로젝트 하위 ID를 직접 요청해 데이터 조회·변경 성공이 0건인지 확인한다.

### User Story 2 테스트 (구현 전에 실패해야 함)

- [x] T026 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/contract/AccessIsolationContractTest.java`에 `anonymous-project-read`, `foreign-project-read`, `unknown-project-read`가 각각 기대한 401 또는 동일 404 `PROJECT_NOT_FOUND`인지 작성한다.
- [x] T027 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectOwnershipGuardTest.java`에 모든 보호 작업의 소유권 검사와 타인 프로젝트 데이터 미반환·미변경 테스트를 작성한다.
- [x] T028 [P] [US2] `services/core-api/src/test/java/com/agentfit/coreapi/project/CrossProjectChildIsolationTest.java`에 `cross-project-child` fixture로 초안·추천·미리보기·승인 하위 ID 교차 요청 거부 테스트를 작성한다.

### User Story 2 구현

- [x] T029 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectOwnershipGuard.java`에 인증 주체·프로젝트 소유권·프로젝트 상태·하위 리소스 귀속을 공통으로 검사하는 경계를 구현하고, 프로젝트 조회·입력·분석·수정·삭제 경로에서 공통 호출하도록 연결한다.
- [x] T030 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectQueryService.java`에 인증 실패와 타인·없는 프로젝트의 정보 은닉을 동일한 의미로 처리한다.
- [x] T031 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/project/ChildResourceOwnershipGuard.java`에 URL 프로젝트와 하위 객체의 실제 귀속 프로젝트 일치 검사를 구현한다.
- [x] T032 [US2] `services/core-api/src/main/java/com/agentfit/coreapi/error/ApiExceptionHandler.java`에 존재 여부·이름·버전·하위 데이터가 오류 응답에 섞이지 않는지 확인하고 로그에도 민감한 프로젝트 정보를 남기지 않도록 정리한다.
- [ ] T033 [US2] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectAccessWebMvcTest.java`에 직접 URL 우회 요청, 타인 프로젝트, 없는 프로젝트, 교차 하위 리소스의 401/동일 404·무변경을 추가한다.

**Checkpoint**: 보호 작업 표본에서 비로그인·비소유 사용자의 타인 데이터 조회·변경 성공이 0건이어야 한다.

---

## Phase 5: User Story 3 - 클라이언트가 소유자나 상태를 위조하지 못하기 (Priority: P2)

**목표**: 서버 생성 필드 위조를 차단하고, 오래된 버전의 동시 수정이 조용히 덮어쓰지 않게 한다.

**독립 테스트**: 위조 필드 요청과 같은 프로젝트에 서로 다른 기준 버전으로 보낸 두 변경을 실행해 위조·두 번째 변경이 저장되지 않는지 확인한다.

### User Story 3 테스트 (구현 전에 실패해야 함)

- [x] T034 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectInputBoundaryTest.java`에 실제 `POST /api/projects`와 `DELETE /api/projects/{projectId}`의 ownerId·서버 상태·검증 성공 여부·출처·Audit 주입 거부 테스트를 작성한다.
- [x] T035 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectWriteGuardTest.java`에 이후 Profile·분석·하위 업무 쓰기가 재사용할 `ProjectWriteGuard`의 `stale-protected-write` fixture, 첫 변경 보존, 두 번째 저장 금지, 충돌 안내, 최신 조회 가능 테스트를 작성한다.
- [ ] T036 [P] [US3] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectConcurrencyIntegrationTest.java`에 동시 요청의 PROJECT 선행 잠금과 버전 증가가 한 번만 성공하는 PostgreSQL 경쟁 테스트를 작성한다.

### User Story 3 구현

- [x] T037 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectWriteCommand.java`에 실제 HTTP endpoint를 추가하지 않는 내부 보호 쓰기 명령으로 projectId·기준 version만 표현하고 ownerId·서버 상태·Audit 필드를 제외한다.
- [x] T038 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectWriteGuard.java`에 이후 Profile·분석·하위 업무 쓰기가 호출할 PROJECT 선행 잠금·기준 version 확인·충돌 시 저장 금지 경계를 구현한다.
- [ ] T039 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectWriteGuard.java`에 보호 업무 변경·version 증가·Audit을 하나의 트랜잭션으로 실행하는 callback 경계를 구현한다. 프로젝트 자체 수정 endpoint는 추가하지 않는다.
- [x] T040 [US3] `services/core-api/src/main/java/com/agentfit/coreapi/error/VersionConflictException.java`와 오류 응답에 승인된 충돌 상태·본문·최신 조회 안내를 반영한다.
- [ ] T041 [US3] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectWriteGuardPostgresIntegrationTest.java`에 재사용 경계의 위조 입력 거부, 오래된 기준 version, PROJECT 선행 잠금, 데이터 불변·Audit 원자성 검증을 추가한다.

**Checkpoint**: 자동 병합·자동 치환·조용한 덮어쓰기 없이 사용자가 충돌을 인지하고 최신 정보를 다시 조회할 수 있어야 한다.

---

## Phase 6: User Story 4 - 프로젝트를 안전하게 지우기 (Priority: P2)

**목표**: 소유자의 명시적 삭제만 프로젝트 종속 A·B 데이터와 Audit을 제거하고, 늦은 분석·Review·추천 결과가 복원되지 않게 한다.

**독립 테스트**: 삭제와 실행 중 작업의 늦은 결과 저장을 겹쳐 프로젝트·종속 데이터가 남지 않고 공용 Catalog·USER·Spring Session JDBC 데이터·사용자 PC 파일은 보존되는지 확인한다.

### User Story 4 테스트 (구현 전에 실패해야 함)

- [ ] T042 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectDeletionTest.java`와 `ProjectDeletionWebMvcTest.java`에 구현보다 먼저 `unconfirmed-delete`, 오래된 `expectedVersion` 삭제의 409·무삭제, 비소유 삭제, CSRF 없는 삭제, 명시 확인과 현재 version 삭제의 성공·무삭제와 공개 오류 응답 테스트를 작성한다.
- [ ] T043 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectDeletionGraphIntegrationTest.java`에 Profile·문서 최소 메타데이터·분석 시도·Review·추천·설정·Export·Audit 삭제와 Catalog·USER·Spring Session JDBC 데이터 보존 테스트를 작성한다.
- [ ] T044 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/project/LateResultAfterDeleteTest.java`에 `running-work-delete`, `late-result-after-delete`, `logout-during-analysis` fixture의 작업 미강제취소, 로그아웃·세션 만료·브라우저 연결 종료 뒤 결과 저장, 삭제 상태·세대·존재 재검증, 복원 금지와 A6 `PROCESSING`/deadline 만료 결과 거부 테스트를 작성한다.
- [ ] T045 [P] [US4] `services/ai-service/tests/test_internal_analysis_auth.py`에 유효 HMAC, 현재 keyId·secret 쌍 누락·이전 key 세 값 일부 누락 시 시작 실패, 만료 이전 key 거부, body hash 변조, ±5분 timestamp 만료, 알 수 없는 keyId, 같은 `requestId`·body hash의 처리 접수 뒤 10분 내 재전송, 처리 전 503 뒤 1회 재시도 허용, DB·Spring Session JDBC 테이블·사용자 권한 미접근, secret·서명·raw body 미기록, 모든 내부 인증 실패의 401 `INTERNAL_AUTH_FAILED` 테스트를 먼저 작성한다.
- [ ] T046 [P] [US4] `services/core-api/src/test/java/com/agentfit/coreapi/audit/AuditTransactionTest.java`에 성공 변경·버전·Audit 원자성, 실패 롤백, 실패 기록의 민감정보 제외 테스트를 작성한다.

### User Story 4 구현

- [ ] T047 [US4] `services/core-api/src/main/java/com/agentfit/core-api/project/ProjectDeletionService.java`에 프로젝트 잠금·인증·소유권·명시 확인·현재 `expectedVersion`·활성 역참조 검증과 종속 삭제 순서를 하나의 트랜잭션으로 구현한다. stale version은 어떤 삭제도 수행하지 않고 `409 VERSION_CONFLICT`로 종료한다.
- [x] T048 [US4] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectDeletionScope.java`에 삭제 대상(Profile, 문서 최소 메타데이터, 분석 시도, Review, 추천, 설정, Export, 프로젝트 Audit)과 보존 대상(Catalog, 사용자 계정, Spring Session JDBC 세션, 사용자 PC 파일)을 명시한다.
- [ ] T049 [US4] `services/core-api/src/main/java/com/agentfit/coreapi/project/ProjectDeleteRequest.java`와 `ProjectController.java`에 승인된 명시 확인·필수 `expectedVersion` 입력, CSRF 보호, stale deletion의 409와 삭제 응답 계약을 구현한다.
- [ ] T050 [US4] `services/core-api/src/main/java/com/agentfit/coreapi/analysis/AnalysisExecutionService.java`, `AiAnalysisClient.java`, `project/ResultPersistenceGuard.java`, `src/main/resources/db/migration/`에 A6 커밋 뒤 브라우저 HTTP 연결·사용자 세션과 독립된 Core API 소유 실행, `HmacKeyRing`을 이용한 `/internal/v1/analysis` header와 exact canonical 문자열(`requestId`·`generation`·`contractVersion`, UTF-8 raw body hash, padding 없는 base64url 서명) 생성, 제한형 재시도, 결과 저장 직전 프로젝트 존재·삭제 상태·소유권·버전·세대·A6 상태·deadline·DRAFT 기준 재검증과 무효 결과 거부를 구현한다. A6의 확정 필드·상태·deadline 제약만 담는 `ANALYSIS_ATTEMPT` Flyway migration을 함께 작성하며, `CONTRACT_TEST_DOUBLE`은 DRAFT·추천 업무 데이터로 변환하지 않는다.
- [x] T051 [US4] `services/ai-service/app/settings.py`, `internal_auth.py`, `internal_analysis.py`, `main.py`에 환경 key ring 시작 검증, `/internal/v1/analysis` HMAC-SHA256·timestamp·keyId·10분 중복 억제 검증과 동일한 401 `INTERNAL_AUTH_FAILED` 응답을 구현한다. 유효 요청은 `{ "result": { "kind": "CONTRACT_TEST_DOUBLE" } }`만 반환한다. DB·Spring Session JDBC 테이블·사용자 권한 조회를 추가하지 않는다.
- [ ] T052 [US4] `services/core-api/src/main/java/com/agentfit/coreapi/audit/AuditRecorder.java`를 성공 업무 변경·삭제와 같은 트랜잭션에 연결하고, 거부·실패는 안전한 최소 기록 경로로 남긴다.
- [ ] T053 [US4] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectDeletionWebMvcTest.java`에 T042의 사전 실패 테스트를 실제 controller·CSRF 설정으로 실행해 비소유·미확인·CSRF 없는 삭제 거부, stale `expectedVersion`의 409·무삭제, 소유자 확인과 현재 version 삭제 성공, 삭제 후 404 및 응답 정보 은닉 회귀를 추가한다.
- [ ] T054 [US4] `services/core-api/src/test/java/com/agentfit/coreapi/project/ProjectDeletionPostgresIntegrationTest.java`에 삭제 그래프·FK·잠금·지연 결과 경쟁을 실제 PostgreSQL 트랜잭션으로 검증한다.

**Checkpoint**: 삭제 후 프로젝트 또는 종속 데이터가 늦은 결과로 되살아나는 경우가 0건이어야 한다.

---

## Phase 7: Polish 및 공통 검증

**목적**: 모든 스토리의 계약·문서·보안·리뷰 품질을 최종 확인한다.

- [ ] T055 [P] `services/core-api/src/test/java/com/agentfit/coreapi/contract/ProjectOwnershipContractTest.java`와 `services/ai-service/tests/test_internal_analysis_contract.py`에 `contracts/project-ownership.md`, `contracts/openapi/core-api.yml`, `contracts/openapi/ai-service.yml`의 OAuth redirect·`AGENTFIT_SESSION`/CSRF cookie·get-session·소유권·삭제·A6/HMAC fixture가 같은 의미인지 계약 테스트를 완성한다. OAuth callback의 GitHub ID 전용 USER 조회·생성, 자동 연결 금지, Location의 `error`·`code`·`state`·token 미노출, 성공 `AGENTFIT_SESSION` 발급과 실패·취소 cookie 미발급, get-session의 공개 필드와 미로그인 `200 null`, stale 삭제 409, camelCase wire 문맥, body hash 변조, timestamp 만료, HMAC 검증 실패, `CONTRACT_TEST_DOUBLE` 200, 제한형 1회 재시도, 로그아웃·세션 만료·브라우저 연결 종료 뒤 유효 결과 저장도 포함한다.
- [ ] T056 [P] `services/core-api/src/test/java/com/agentfit/coreapi/project/OwnershipSecurityRegressionTest.java`에 직접 API 우회, ID 추론, ownerId 위조, CSRF 누락·불일치, `AGENTFIT_SESSION` 쿠키 없음·만료·폐기 서버 세션, 타인 프로젝트 조회·변경 회귀 테스트를 추가한다.
- [x] T057 [P] `specs/002-project-ownership/quickstart.md`에 승인된 실제 URL·DTO·오류 본문과 실행 명령을 반영하고, 스테이징에서 정적 Frontend와 `/api` proxy가 같은 Origin인지, HTTPS의 `AGENTFIT_SESSION`/`XSRF-TOKEN` cookie 속성, GitHub callback URL, USER GitHub ID 조회·생성, 로그인·CSRF·logout 왕복을 검증한다. 환경이 없으면 NOT_RUN 사유와 미실행 범위를 기록한다.
- [ ] T058 `services/core-api/pom.xml`, `services/core-api/src/main/java/`, `services/core-api/src/test/java/`, `services/ai-service/app/`, `services/ai-service/tests/`, `contracts/openapi/core-api.yml`, `contracts/openapi/ai-service.yml`의 중복·불필요한 의존성·계약 불일치를 정리한다.
- [x] T059 `services/core-api`에서 `mvn test`와 PostgreSQL 기반 통합·경쟁 테스트를, `services/ai-service`에서 `pytest`를 실행하고, Maven·Python·DB 미설정이면 성공으로 표시하지 않고 원인과 미실행 범위를 기록한다.
- [x] T060 `specs/002-project-ownership/plan.md`와 `specs/002-project-ownership/spec.md`의 FR-OWN-001~013 구현 상태·테스트 근거를 갱신한다.
- [ ] T061 `specs/002-project-ownership/`와 변경된 Core API·AI Service 소스 전체에 대해 작성자와 독립된 코드 리뷰를 수행해 버그, 요구사항 누락, 소유권·동시성·민감정보·계약 불일치, 불필요한 중복을 기록하고 수정한다.

---

## 의존성 및 실행 순서

### 단계 의존성

- **Phase 1**: 기존 스캐폴드 확인 후 즉시 시작 가능하나, T001~T004 계약 승인 게이트가 완료되어야 구현 작업으로 넘어간다.
- **Phase 2**: Phase 1과 승인된 계약에 의존한다. 인증·오류·잠금·물리 모델·Audit 기반이 모든 사용자 스토리를 차단한다.
- **Phase 3~6**: Phase 2 완료 후 진행한다. P1인 US1과 US2를 먼저 MVP 후보로 삼고, 이후 P2인 US3·US4를 진행한다.
- **Phase 7**: 필요한 사용자 스토리와 통합 테스트가 완료된 뒤 진행한다.

### 사용자 스토리 의존성

- **US1 (P1)**: Phase 2 이후 시작. 프로젝트 생성·목록·상세의 최소 MVP다.
- **US2 (P1)**: Phase 2 이후 시작 가능하지만 US1의 Project 모델과 공통 소유권 경계를 사용한다.
- **US3 (P2)**: US1의 프로젝트 쓰기 모델과 Phase 2의 잠금·버전 기반에 의존한다.
- **US4 (P2)**: US1의 프로젝트·하위 리소스 관계와 Phase 2의 Audit·트랜잭션 기반에 의존한다. 실제 LLM 업무가 없어도 HMAC verifier와 결과 저장 경계의 테스트 대역으로 검증할 수 있다.

### 병렬 실행 기회

- Phase 1의 T001~T003은 서로 다른 계약 영역이므로 담당자별 병렬 검토가 가능하다.
- Phase 2에서는 T006~T009와 T015를 구현보다 먼저 병렬로 실패시킨다. 이후 T010~T014와 T016을 담당 파일별로 구현한다. T013 인증 전용 Flyway migration은 즉시 진행할 수 있고, `PROJECT`·`AUDIT` migration은 US1의 T020, `ANALYSIS_ATTEMPT` migration은 US4의 T050에서 작성한다.
- US1의 T017~T019 테스트는 병렬 작성 가능하며 T020~T021 모델·저장소도 병렬 작업할 수 있다.
- US2의 T026~T028 테스트, US3의 T034~T036 테스트, US4의 T042~T046 테스트는 각각 병렬 작성 가능하다.
- Phase 2 완료 후 US1·US2는 팀 용량이 있으면 병렬 진행할 수 있다. US3·US4는 각각 필요한 기반을 공유하므로 파일 충돌을 조정한다.
- Phase 7의 T055~T057은 코드 구현과 독립적으로 병렬 검토할 수 있다.

## 구현 전략

### MVP 우선

1. Phase 1에서 API·세션 계약 게이트를 승인한다.
2. Phase 2에서 인증 주체·오류·소유권 경계·논리/물리 데이터 기반을 준비한다.
3. Phase 3 US1을 완료해 소유자별 생성·목록·상세를 검증한다.
4. Phase 4 US2를 완료해 우회 요청·하위 리소스 격리를 검증한다.
5. **중지 후 검증**: US1·US2를 독립 테스트하고 보안 인수 기준을 확인한다.

### 점진적 확장

1. US3에서 위조 입력 차단과 버전 충돌을 추가한다.
2. US4에서 삭제 그래프·Audit 원자성·지연 결과 차단을 추가한다.
3. B·AI의 실제 모델 기능이 연결될 때 T050의 결과 저장 경계와 T051의 HMAC verifier를 재사용해 권한·세대·버전 검증을 유지한다.
4. 각 단계마다 계약, 웹, PostgreSQL 트랜잭션·경쟁 테스트를 통과시킨다.

## 추적성 및 완료 기준

- FR-OWN-001~004: T001~T007, T010, T017~T025
- FR-OWN-005~008: T007, T011, T026~T033
- FR-OWN-009: T008, T012, T034~T041
- FR-OWN-010~012: T009, T014~T016, T042~T054
- FR-OWN-013: T001~T004, T055~T060
- 모든 테스트 작업은 구현 작업보다 먼저 작성되어야 하며, 실제로 실패하는지 확인한 뒤 구현한다.
- 모든 완료된 보호 작업에서 비로그인·비소유 사용자의 타인 데이터 조회·변경 성공은 0건이다.
- 충돌 시 조용한 덮어쓰기 0건, 삭제 후 지연 결과 복원 0건, 민감정보 Audit 저장 0건이다.
- OpenAPI·DTO·서버 검증·fixture·테스트가 승인된 계약 버전을 공동으로 가리킨다.
- T061 독립 리뷰와 T059 실행 결과가 남기 전에는 기능 완료로 표시하지 않는다.
