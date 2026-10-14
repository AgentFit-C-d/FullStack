# 조사: 프로젝트 소유권과 접근 격리

**Feature**: [프로젝트 소유권과 접근 격리](./spec.md)
**Date**: 2026-10-06
**Purpose**: 구현 방식이 아닌 책임 경계와 계약 결정을 정리한다.

## Decision 1 — 업무 데이터의 단일 쓰기 주체는 Core API다

**Decision**: 인증 주체, 프로젝트 소유권, 버전, Audit, PostgreSQL 읽기·쓰기는 Spring Boot Core API에서만 처리한다.

**Rationale**: 최신 통합 개발기준과 헌장은 브라우저→Core API→AI Service 경로, Core API의 PostgreSQL 단독 접근, A/B의 단일 Core API 개발을 확정한다. 권한 판정과 DB 변경을 Frontend나 AI Service에 나누면 같은 프로젝트를 서로 다른 기준으로 바꿀 위험이 생긴다.

**Alternatives considered**:

- Frontend가 소유권을 판단: 화면 우회 요청을 막지 못하므로 제외한다.
- AI Service가 분석 결과와 함께 권한을 판단: AI는 최종 업무 상태·권한을 소유하지 않으므로 제외한다.
- A와 B를 별도 HTTP 서비스로 분리: 같은 프로젝트의 원자 작업을 복잡하게 만들므로 제외한다.

## Decision 2 — Spring Boot가 인증과 소유권 검증을 통합한다

**Decision**: Spring Boot가 GitHub OAuth callback·token 교환·PostgreSQL 기반 Spring Session JDBC 세션 발급을 담당한다. GitHub 단일 provider와 `user:email` scope만 허용하고 저장소 권한을 요청하지 않는다. OAuth callback은 기존 USER의 `github_user_id BIGINT`로만 사용자를 조회하며, 일치하지 않으면 `github_user_id`·`github_login VARCHAR(255)`를 채운 새 USER를 만든다. 기존 USER의 null GitHub ID는 보존하고 null이 아닌 ID만 유일하며, 이메일·이름·`github_login` 자동 연결은 하지 않는다. Core API는 이름이 `AGENTFIT_SESSION`인 HttpOnly cookie의 세션 ID로 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`의 `PRINCIPAL_NAME` 내부 userId를 인증 주체로 사용한다. 별도 `OAUTH_ACCOUNT`·AgentFit `SESSION` 테이블과 GitHub provider access/refresh token 저장은 만들지 않는다.

**Rationale**: 최신 API 문서는 브라우저에 JWT/Bearer를 전달하지 않고 HttpOnly 세션 쿠키만 사용하며, 서버가 PostgreSQL Spring Session JDBC 기본 테이블을 조회하도록 정한다. 따라서 프로젝트 소유권은 클라이언트 입력이 아닌 서버 세션의 내부 userId에서 결정한다. PostgreSQL 저장은 서버 재시작 뒤에도 세션을 검증할 수 있고 별도 Redis 운영을 추가하지 않는다. 기존 계정을 이메일 등으로 자동 연결하지 않는 원칙은 서로 다른 GitHub 계정의 오연결을 막는다.

**Alternatives considered**:

- 별도 Better Auth/Node 인증 서버를 추가: 정적 Frontend와 Spring Boot·FastAPI 중심 배포에 실행 환경과 세션 연동을 추가하므로 제외한다. Spring Boot가 OAuth callback·token 교환·PostgreSQL 기반 Spring Session JDBC 발급과 `AGENTFIT_SESSION` 검증을 한 경계에서 담당한다.
- 클라이언트가 보낸 ownerId를 신뢰: 요구사항과 소유권 보안 원칙에 반하므로 제외한다.

## Decision 3 — URL보다 오류 의미와 fixture를 먼저 확정한다

**Decision**: Core API OpenAPI에 OAuth 시작·callback, CSRF·logout, 세션, 프로젝트 생성·목록·상세·삭제의 1차 URL과 DTO를 고정한다. 비로그인은 401, CSRF 실패는 403 CSRF_INVALID, 타인과 없는 프로젝트는 동일한 404 PROJECT_NOT_FOUND, 하위 리소스는 URL 프로젝트에 귀속, 공개 결과에는 ownerId가 없고 금지 입력은 거부, 이후 하위 업무의 충돌은 저장하지 않고 최신 정보 재조회를 안내한다. 프로젝트 자체 수정 endpoint는 만들지 않는다.

**Rationale**: 접근 격리 규칙만 먼저 정하고 인증·CSRF wire 형식을 미루면, 정적 Frontend와 Spring Security가 서로 다른 요청 경로·header를 구현할 수 있다. 1차 URL과 오류 의미를 OpenAPI에 함께 고정하면 Frontend, Core API, 테스트가 같은 정상·실패 경로를 검증할 수 있다.

**Alternatives considered**:

- 프로젝트 자체 수정 endpoint를 지금 추가: 이번 범위를 넘고, 409 충돌을 발생시키는 실제 보호 쓰기 대상도 아직 없으므로 제외한다.
- URL이 정해질 때까지 CSRF·오류 규칙도 미룸: 보안 인수 기준을 검증할 수 없으므로 제외한다.

## Decision 4 — 논리 모델만 먼저 정의한다

**Decision**: 이 단계의 데이터 모델은 프로젝트, 인증 주체, 프로젝트 하위 리소스, Audit의 관계와 상태 규칙만 표현한다. ERD, SQL 타입, foreign key, 인덱스, migration 도구·파일은 작성하지 않는다.

**Rationale**: 사용자는 ERD·DB 마이그레이션을 후속 단계로 요청했다. 다만 삭제 범위, 소유권, 버전, Audit이 서로 어떤 관계인지가 없으면 이후 물리 설계에서 요구사항을 놓치기 쉽다.

**Alternatives considered**:

- 지금 DDL과 migration까지 설계: 현재 요청 범위를 넘으므로 제외한다.
- 데이터 관계를 전혀 기록하지 않음: 삭제·Audit·하위 리소스 격리 규칙을 검증할 수 없어 제외한다.

## Decision 5 — 삭제와 지연 결과는 같은 보호 규칙의 일부다

**Decision**: 삭제는 프로젝트 잠금, 인증, 소유권, 명시적 확인과 종속 데이터 정리를 하나의 Core API 작업으로 다룬다. 인증·소유권 검증과 A6 저장을 마친 외부 분석·Review·추천 실행은 사용자 로그아웃·세션 만료·브라우저 연결 종료와 독립된 Core API 소유 작업으로 유지한다. 결과는 저장 직전에 부모 프로젝트와 유효 기준을 재검증해 삭제된 데이터를 복원하지 못하게 한다.

**Rationale**: 최신 기준과 헌장은 삭제 후 지연 결과의 재생성을 금지하고, 외부 AI 호출을 DB 트랜잭션 밖에서 수행하되 저장 전 재검증을 요구한다. 로그인 세션은 작업 접수 시점의 인증 경계이지, 이미 접수한 서버 작업의 수명이나 결과 저장 근거가 아니다.

**Alternatives considered**:

- 결과 요청 시점의 권한 검사만 수행: 삭제 이후 도착한 결과가 프로젝트를 다시 만들 수 있어 제외한다.
- 삭제 성공 후 Audit을 별도 장기 보관: 현재 기준은 프로젝트 종속 Audit도 제거하도록 하므로 별도 보관 정책이 승인되기 전에는 제외한다.

## Decision 6 — 프로젝트 삭제는 종속 데이터만 제거하고 작업은 취소하지 않는다

**Decision**: 프로젝트 종속 A·B 업무 데이터만 삭제한다. 공용 Catalog, 사용자 계정, 인증 Spring Session JDBC 데이터, 사용자 PC 파일은 보존한다. 실행 중인 분석·Review·추천 작업을 강제 취소하는 API는 MVP에 포함하지 않고, 삭제 상태·세대·프로젝트 존재 여부를 결과 저장 직전에 검증해 늦은 결과를 거부한다.

**Rationale**: Catalog와 계정·세션은 프로젝트 소유 데이터가 아니며, 사용자 PC 파일도 Core API가 소유하지 않는다. 작업 취소와 결과 저장 거부는 서로 다른 문제이므로, MVP에서는 데이터 복원을 막는 저장 시점 검증을 우선한다.

**Alternatives considered**:

- 공용 Catalog까지 삭제: 다른 프로젝트의 기준 데이터를 훼손하므로 제외한다.
- 삭제 시 모든 외부 작업을 강제 취소: 외부 worker·AI 제공자의 취소 계약이 필요하고 현재 범위를 넓히므로 제외한다.
- 결과 요청 시점에만 검사: 삭제 이후 도착한 결과가 데이터를 복원할 수 있어 제외한다.

## Decision 7 — Spring Boot 서버 세션을 Core API가 소비한다

**Decision**: 로그인은 GitHub OAuth로 수행하되, 로그인 이후 AgentFit API의 인증은 자체 서버 세션으로 처리한다. 정적 Frontend와 Spring Boot Core API는 같은 Origin에서 제공하고 Core API는 `/api` 경로로 프록시한다. OAuth 시작과 callback은 `/api/oauth2/authorization/github`, `/api/login/oauth2/code/github`으로 고정하며, GitHub OAuth App redirect URI와 proxy도 callback 경로를 사용한다. Spring Boot는 GitHub callback과 최초 token 교환 뒤 `github_user_id`로만 기존 USER를 찾고, 없으면 `github_user_id BIGINT`·`github_login VARCHAR(255)`를 채운 새 USER를 만든다. 기존 null GitHub ID는 보존하며 이메일·이름·login 자동 연결은 하지 않는다. Spring Boot는 PostgreSQL Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`를 발급·관리하고 `PRINCIPAL_NAME`에 내부 userId를 연결한다. Core API는 이름이 `AGENTFIT_SESSION`인 host-only, `Path=/api`, `HttpOnly`, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age=7일` 지속 세션 쿠키로 인증된 userId를 얻는다. 서버 세션은 마지막 인증된 요청 후 7일 비활성 시 만료하고, 쿠키는 발급 뒤 7일에 만료되며 자동 연장하지 않는다. 둘 중 먼저 만료되거나 로그아웃하면 DB 세션과 브라우저 쿠키를 함께 즉시 폐기한다. 개발 환경 `localhost`만 `Secure`를 끌 수 있고 CORS credential 설정은 사용하지 않는다. `GET /api/auth/csrf`가 발급한 `Path=/`·비-HttpOnly의 `XSRF-TOKEN`을 상태 변경 요청의 `X-XSRF-TOKEN`으로 검증한다. GitHub provider access/refresh token은 최초 사용자 식별 뒤 폐기하며 DB·Spring Session JDBC 테이블·Audit·일반 로그에 저장하거나 갱신하지 않는다. 브라우저에는 GitHub token을 전달하지 않고, 프로젝트 API에도 JWT·Bearer·provider token·refresh token API를 추가하지 않는다. GitHub provider와 `user:email` scope만 허용하고 저장소 권한은 요청하지 않는다. 세션 만료·로그아웃 뒤에는 GitHub 로그인을 다시 진행한다.

**Why this was chosen**:

- GitHub는 사용자의 신원을 확인하는 외부 provider이고, AgentFit의 프로젝트 소유권·Audit·업무 권한은 AgentFit이 관리해야 한다. 따라서 외부 provider token과 내부 서비스 인증 상태를 분리한다.
- GitHub token을 최초 로그인 이후에도 AgentFit API 인증에 사용하면 매 요청마다 GitHub 검증 또는 token 보관이 필요해지고, GitHub 장애·rate limit·token 폐기가 AgentFit API 인증 장애로 전파된다.
- 자체 세션을 사용하면 Core API가 PostgreSQL의 세션과 `userId`를 기준으로 프로젝트 소유권을 일관되게 판정할 수 있고, 브라우저에는 HttpOnly 쿠키만 남길 수 있다.
- PostgreSQL 세션은 서버 재시작 뒤에도 인증 상태를 검증할 수 있고, 현재 배포 경계에 별도 Redis 세션 인프라를 추가하지 않는다.
- 현재 제품 범위는 GitHub 저장소를 사용자를 대신해 읽거나 쓰는 기능이 아니라 로그인과 AgentFit 프로젝트 관리이므로, GitHub access/refresh token을 장기 보관할 필요가 없다.

**Alternatives rejected**:

- **GitHub access token을 AgentFit API의 bearer token으로 직접 사용**: GitHub token은 AgentFit의 프로젝트 권한·소유권을 표현하지 않는다. 요청마다 외부 검증을 하거나 token을 장기 보관해야 하므로 provider 의존성, 장애 전파, token 탈취 영향이 커져 제외한다.
- **GitHub access/refresh token을 저장하고 자동 갱신**: GitHub API를 사용자 대신 호출해야 하는 기능에는 필요할 수 있지만, 현재 범위에는 불필요하다. 암호화 저장, refresh token 회전, 폐기, 침해 대응이라는 운영 책임을 추가하므로 제외한다.
- **Frontend가 GitHub token을 보관하고 요청마다 전달**: token이 브라우저 저장소·네트워크·로그에 노출될 위험이 커지고, provider token을 내부 API 경계로 확장하므로 제외한다.
- **AgentFit 자체 JWT 발급**: 서버 세션 대신 무상태 인증을 얻지만, 이번 MVP에서는 토큰 폐기·로그아웃·만료 상태를 별도로 관리해야 하고 PostgreSQL 기반 세션 계약과 맞지 않아 제외한다.
- **별도 Better Auth/Node 인증 서버 추가**: 정적 Frontend와 Spring Boot·FastAPI 중심 배포에 실행 환경과 세션 연동을 추가하므로 제외한다.

**Approval record**: 2026-10-08, 승인 주체: 사용자(A 담당). 근거: `Docs/api/common.md`, `Docs/api/README.md`, `Docs/api/01-project-analysis.md`, 최신 통합 개발기준의 책임 경계, GitHub OAuth 보안 권장사항.

**Unresolved**: USER 확장과 Spring Session JDBC 기본 schema는 `services/core-api/src/main/resources/db/migration/`의 Flyway versioned SQL로 적용한다. 다음 migration 버전 번호와 인증 외 프로젝트·하위 업무 데이터 DDL의 분할·적용 순서, 실제 LLM 입력·출력 업무 규칙은 구현 설계에서 정한다. OAuth 성공은 `/?login=success`, 실패·취소는 `/?login=failed`로 이동하며 provider의 error·token·code·state는 URL에 노출하지 않는다.

## Decision 8 — HMAC 내부 호출은 AI OpenAPI에 고정한다

**Decision**: Core API는 `POST /internal/v1/analysis`로 AI Service를 호출한다. A6의 `request_id`·`generation`·`contract_version`은 HTTP body의 `requestId`·`generation`·`contractVersion`으로 매핑한다. `X-AgentFit-Key-Id`, `X-AgentFit-Timestamp`, `X-AgentFit-Signature`를 사용하며, canonical 문자열은 HTTP method, path, timestamp, 세 camelCase 문맥, UTF-8 raw body의 소문자 SHA-256 hex를 줄바꿈 순서대로 연결한다. HMAC-SHA256 결과는 padding 없는 base64url로 전송한다. AI Service는 timestamp ±5분, 현재 또는 아직 유효한 이전 keyId, 요청 형식과 처리 접수를 확정한 뒤 10분간 같은 `requestId`·body hash 중복을 검증한다. 아직 처리하지 않았음을 보장한 503은 fingerprint를 남기지 않아 Core API가 최대 1회 재시도할 수 있다. HMAC·timestamp·중복 검증 실패는 모두 `401 INTERNAL_AUTH_FAILED`로 처리한다. 실제 LLM 규칙이 없는 현재 기능의 성공 응답은 `result.kind = CONTRACT_TEST_DOUBLE`이다.

**Key lifecycle**: 두 서비스는 `AGENTFIT_HMAC_CURRENT_KEY_ID`와 `AGENTFIT_HMAC_CURRENT_SECRET`을 반드시 함께 받아야 하며 하나라도 없으면 시작에 실패한다. 이전 키를 구성할 때는 `AGENTFIT_HMAC_PREVIOUS_KEY_ID`, `AGENTFIT_HMAC_PREVIOUS_SECRET`, `AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL`을 모두 함께 제공한다. 이전 키는 유효 시각 이후 거부하고, 세 값 중 일부만 있으면 시작에 실패한다. Secret, HMAC 서명, raw body는 DB·Audit·일반 로그에 남기지 않는다.

**Rationale**: HMAC은 사용자 권한 검증이 아니라 Core API에서 온 변조되지 않은 내부 요청인지 확인하는 장치다. header·canonical 문자열·오류를 Core API 문서에만 적으면 FastAPI가 다르게 해석할 수 있으므로, AI Service OpenAPI를 단일 공유 기준으로 둔다. 10분 중복 억제는 FastAPI의 짧은 수신 보호이고, 재시작 뒤 중복 결과 저장 차단은 A6·PROJECT 재검증을 하는 Core API가 맡는다.

**Alternatives considered**:

- 브라우저 세션 cookie 또는 JWT를 AI Service로 전달: AI Service가 사용자 인증 경계와 token 노출 범위에 결합되므로 제외한다.
- FastAPI가 PROJECT·Spring Session JDBC 테이블을 직접 조회: 단일 쓰기 소유권과 권한 책임 경계를 깨므로 제외한다.
- HMAC 세부 계약을 실제 AI 모델 기능까지 미룸: 결과 저장 보호 경계를 테스트할 수 없고 서비스 간 구현이 어긋날 위험이 있으므로 제외한다.

## 조사 결론

계획에 남은 항목은 모호한 구현 과제가 아니라 명시적인 승인 게이트다. 인증·CSRF·프로젝트·HMAC 호출의 공유 계약과 USER 확장·Spring Session JDBC 기본 schema·`AGENTFIT_SESSION` cookie 이름·Flyway migration 방식은 승인 기록을 남겼다. 프로젝트·하위 업무 데이터 DDL의 상세와 LLM 입력·출력 업무 규칙을 확정하기 전에는 해당 부분을 구현 완료로 표시하지 않는다.

## Convergence 2026-10-13

초기 Decision 7의 `Path=/api` 표현은 구현 수렴 과정에서 `Path=/`로 정정한다. 실제 `SecurityConfig.java`는 `/`와 `/api` 모두에 `AGENTFIT_SESSION`을 전달해야 하는 현재 same-origin 구조를 반영해 `Path=/`를 사용한다. Spring Security `maximumSessions(1)`과 Spring Session-backed `SessionRegistry`도 구현된 인증 정책으로 문서화한다. 기존 결정 문장은 당시 승인 기록으로 보존하며, 현재 동작 기준은 `spec.md`의 Convergence 항목과 `quickstart.md`를 따른다.

