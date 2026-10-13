# Feature Specification: 프로젝트 소유권과 접근 격리

**Feature Branch**: `[002-project-ownership]`  
**Created**: 2026-10-06  
**Status**: Implementation complete; automated verification passed; PostgreSQL/deployment smoke verification pending
**Input**: 개인 소유 프로젝트, 인증 주체 기반 소유자 결정, 전 보호 작업의 소유권 검사, 정보 은닉, 하위 리소스 격리, 안전한 삭제를 명세화한다.

## 근거와 상태

이 명세는 구현 완료 여부를 뜻하지 않는다. 최신 통합 개발기준과 사용자가 제공한 세부 계약 문서를 함께 읽되, 각 문장의 상태를 분리한다.

| 상태 | 이 명세에서의 내용 | 근거 |
| --- | --- | --- |
| 확정된 제품 원칙 | 개인 소유 프로젝트, 서버 인증 주체에 의한 소유권 판단, 모든 보호 작업의 소유권 검증, 프로젝트 잠금·버전, 원자적 삭제와 Audit | `Docs/AgentFit_AB_1차통합_개발기준.pdf` p.4, p.6, p.9, p.35 |
| 사용자 지정 세부 계약 | Spring Boot가 GitHub OAuth와 서버 세션을 처리하고 HttpOnly 세션 쿠키로 인증하며, 비로그인 401, 타인·없는 프로젝트를 같은 404로 처리, 공개 DTO에서 `ownerId` 비공개, 하위 객체의 프로젝트 귀속 | `Docs/api/common.md:9,20~30,53`, `Docs/api/README.md:60`, `Docs/api/openapi.phase1.json:2089`, `Docs/PRD.md:143,198` |
| 구현 방식 미결정 | 실제 AI 모델 처리 규칙 | Spring Session JDBC의 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` 기본 schema, GitHub 단일 provider용 USER 필드와 DB 타입, 기존 USER를 보존하는 단계적 migration 원칙, 세션 cookie 이름과 Flyway migration 방식은 확정했지만 LLM 업무 규칙은 확정하지 않음 |

제공된 API 문서는 스스로 “구현·실제 호출 검증 전인 설계 계약”이라고 밝힌다. 그러나 사용자가 이번 기능 명세에 명시적으로 포함한 세부 동작은 아래 요구사항과 인수 기준으로 채택한다. 구현을 시작하기 전에는 최신 PDF의 원칙과 OpenAPI·fixture를 다시 일치시켜야 한다.

## Clarifications

### Session 2026-10-06

- Q: 오래된 프로젝트 버전으로 수정 요청이 오면 사용자에게 어떻게 처리해야 할까요? → A: 충돌을 알리고 저장하지 않는다. 사용자가 최신 정보를 다시 불러온다.

### Session 2026-10-07

- Q: GitHub 로그인 후 Core API에 인증 주체를 어떻게 전달할까요? → A: Spring Boot가 OAuth callback 뒤 서버 세션을 만들고 HttpOnly 세션 쿠키를 발급한다. Core API는 쿠키의 세션 토큰으로 PostgreSQL Spring Session JDBC의 `SPRING_SESSION`을 조회해 userId를 확인한다. 브라우저에는 JWT·Bearer·provider token을 전달하지 않으며, 클라이언트의 userId·ownerId는 사용하지 않는다.
- Q: Spring Boot의 서버 세션을 운영 환경에서 어디에 저장할까요? → A: PostgreSQL 기반 Spring Session으로 저장한다. 서버 메모리 세션과 Redis 세션은 채택하지 않으며, 서버 재시작 뒤에도 유효한 세션을 DB에서 검증한다.
- Q: 로그인 상태는 얼마나 유지할까요? → A: 마지막 인증된 요청 후 7일간 활동이 없으면 PostgreSQL 서버 세션을 만료한다. 로그아웃 시에는 DB 세션과 브라우저 쿠키를 함께 즉시 폐기한다. 자동 로그인·refresh token 갱신은 제공하지 않는다.
- Q: GitHub OAuth에서 받은 access token과 refresh token을 로그인 뒤에도 저장할까요? → A: 로그인 중 GitHub 사용자 식별·이메일 조회에만 사용한 뒤 즉시 폐기한다. DB·Spring Session JDBC 테이블·Audit·일반 로그에 영속 저장하지 않으며 refresh token 갱신 흐름도 구현하지 않는다.
- Q: 프로젝트 삭제 범위와 실행 중 작업은 어떻게 처리할까요? → A: 프로젝트에 종속된 A·B 업무 데이터는 모두 삭제하고, 공용 Catalog·사용자 계정·Session·사용자 PC 파일은 삭제하지 않는다. 실행 중인 분석·Review·추천 작업은 강제 취소하지 않고, 삭제 상태·세대·프로젝트 존재 여부를 재검증해 늦은 결과의 저장과 복원을 차단한다.
- Q: Spring Boot와 FastAPI의 인증 책임을 어떻게 나눌까요? → A: Spring Boot가 GitHub OAuth, HttpOnly 세션 쿠키, 기존 USER의 `github_user_id`·`github_login`, Spring Session JDBC 기본 세션 테이블 관리를 담당한다. Core API는 세션을 조회해 사용자 context와 프로젝트 소유권을 검증하고 도메인 DB·Audit·버전·트랜잭션을 관리한다. Spring Boot가 FastAPI를 호출할 때 HMAC을 첨부하고, FastAPI는 HMAC을 검증한 뒤 AI 처리만 수행한다. FastAPI는 사용자 권한·프로젝트 소유권·최종 확정 상태를 판단하지 않는다.
- Q: 분석 시작 뒤 사용자가 로그아웃하거나 브라우저 연결이 끊기면 AI 작업과 결과 저장은 어떻게 처리할까요? → A: 인증·소유권 검증 뒤 A6 `ANALYSIS_ATTEMPT`를 커밋한 작업은 브라우저 세션과 연결 상태에 종속되지 않는 Core API 소유 작업으로 처리한다. 로그아웃·세션 만료·연결 종료는 새 요청과 결과 조회만 막으며, AI 결과는 프로젝트·A6·세대·삭제 상태를 재검증한 뒤 유효할 때만 저장한다. 결과 전달이 끊긴 사용자는 다시 로그인해 저장된 결과를 조회한다.
- Q: Redis 없이 Spring Boot와 FastAPI의 분석 요청 중복을 어떤 데이터로 추적할까요? → A: PDF의 A6 `ANALYSIS_ATTEMPT`를 분석 시작 시 먼저 생성한다. A6 내부의 `request_id`·`contract_version` 값은 HTTP body에서 각각 `requestId`·`contractVersion`으로 전달하고 `generation`과 함께 HMAC 서명 대상에 포함한다. A6의 프로젝트·세대·상태·기한 검증이 동일 분석 시도의 중복·지연 결과 기준이 되며, HMAC secret과 서명값은 A6에 저장하지 않는다. `requestId`만으로 멱등성을 주장하지 않고 PROJECT 잠금·활성 `PROCESSING` 확인·결과 저장 직전 재검증을 함께 적용한다.
- Q: FastAPI timeout·5xx·HMAC 검증 실패를 반환하면 Spring Boot는 자동 재시도할까요? → A: 연결 전에 실패했거나 AI Service가 아직 처리하지 않았음을 보장한 503인 경우에만 같은 `requestId`·`generation`으로 최대 1회 재시도한다. 요청을 보낸 뒤 응답이 없는 timeout 또는 처리 여부가 불명확한 5xx는 자동 재시도하지 않고 A6를 `PROCESSING`으로 유지한 뒤 deadline 초과 시 `FAILED`로 종료한다. HMAC 검증 실패는 재시도하지 않는다.
- Q: 구현을 시작할 때 HMAC 계약의 기본값을 확정해도 될까요? → A: HMAC-SHA256을 사용하고 HTTP method·path·timestamp·`requestId`·`generation`·`contractVersion`·실제 전송 UTF-8 body의 SHA-256 소문자 hex를 줄바꿈으로 연결해 서명한다. timestamp는 서버 시간 기준 ±5분만 허용하며, `keyId`로 현재 키와 이전 키를 짧은 교체 기간 동안 함께 허용한다. 서명은 padding 없는 base64url 인코딩을 사용한다. HMAC 검증 실패는 재시도하지 않고 A6를 실패 처리한다.
- Q: 프로젝트 API의 1차 경로를 사용자가 제공한 초안대로 확정할까요? → A: `/api/projects`, `/api/projects/{projectId}`, `/api/auth/get-session`을 1차 경로로 채택한다. request/response DTO, 오류 본문, 이름 길이와 삭제 확인 wire 형식은 별도 계약 승인에서 확정한다.
- Q: 프로젝트 자체 수정 기능을 이번 `002-project-ownership` 범위에 포함할까요? → A: 제외한다. 프로젝트 자체는 생성·목록·상세·삭제만 제공하며, 버전 충돌 규칙은 Profile·분석·하위 업무 데이터 저장 등 실제 보호 쓰기에 적용한다.

### Session 2026-10-08

- Q: 브라우저를 닫았다 다시 열어도 로그인 상태를 유지할까요? → A: `Max-Age=7일`의 지속 HttpOnly 세션 쿠키를 발급한다. 서버 세션은 마지막 인증된 요청 후 7일 비활성 시 만료하며, 쿠키 만료 또는 서버 세션 만료 중 먼저 도래한 시점에 다시 GitHub OAuth 로그인을 진행한다. 쿠키는 자동 연장하지 않고, 로그아웃 시 DB 세션과 쿠키를 함께 즉시 폐기한다.
- Q: Frontend와 Core API를 어떤 도메인 관계로 배포할까요? → A: 정적 Frontend와 Spring Boot Core API를 같은 Origin으로 제공하고 Core API는 `/api` 경로로 프록시한다. 운영 쿠키는 host-only, `Path=/`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Max-Age=7일`로 발급한다. `Path=/`는 로그인 성공 redirect인 `/`와 `/api` 요청 모두에 동일한 서버 세션을 전달하기 위한 구현 결정이다. 개발 환경의 `localhost`만 `Secure`를 끌 수 있으며, 같은 Origin이므로 CORS credential 설정은 두지 않는다. 상태 변경 요청은 Spring Security CSRF 보호를 유지한다.
- Q: 세션 쿠키로 OAuth 요청 상태를 어떻게 유지할까요? → A: 로그인 시작 경로를 `GET /api/oauth2/authorization/github`, callback 경로를 `GET /api/login/oauth2/code/github`으로 고정하고, 프록시와 GitHub OAuth App의 redirect URI도 callback 경로를 사용한다. 세션 cookie의 `Path=/`로 OAuth 시작·callback과 로그인 성공 후 `/`의 Frontend 요청 모두에 동일한 세션이 전달된다. 로그인 성공 뒤에는 `/?login=success`, 취소·실패 뒤에는 provider 오류 원문 없이 `/?login=failed`로 이동한다.
- Q: 정적 Frontend가 상태 변경 요청의 CSRF 토큰을 어떻게 보낼까요? → A: `GET /api/auth/csrf`가 JavaScript가 읽을 수 있도록 `Path=/`, 비-HttpOnly, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age` 없는 세션성 `XSRF-TOKEN` 쿠키를 발급하고, Frontend는 `POST`·`PUT`·`PATCH`·`DELETE /api/**` 요청에 같은 값을 `X-XSRF-TOKEN` 헤더로 보낸다. 누락·불일치는 `403 CSRF_INVALID`이며, 이 CSRF 쿠키는 세션 쿠키가 아니다.
- Q: 프로젝트 삭제와 다른 보호 쓰기의 동시 경쟁은 어떻게 막을까요? → A: 삭제도 프로젝트 쓰기이므로 본문에 `{ "confirmation": true, "expectedVersion": number }`를 요구한다. Core API는 `PROJECT` 잠금 뒤 현재 version이 일치할 때만 삭제하고, 오래된 version은 삭제하지 않으며 `409 VERSION_CONFLICT`로 최신 조회를 안내한다.
- Q: PostgreSQL 세션 저장소를 Spring Session JDBC의 기본 테이블로 사용할까요, 아니면 직접 `SESSION` 테이블을 설계할까요? → A: Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` 테이블을 사용한다. `PRINCIPAL_NAME`에는 AgentFit의 내부 사용자 ID를 저장하며, 별도 AgentFit `SESSION` 테이블은 만들지 않는다.
- Q: GitHub 계정 정보를 기존 AgentFit 사용자와 어떤 구조로 연결할까요? → A: 현재 MVP에서는 기존 `USER`에 GitHub 식별자 필드를 직접 추가한다. `github_user_id`는 GitHub의 변경되지 않는 숫자 ID로 유일하게 관리하고, `github_login`은 변경 가능한 표시·로그용 값으로만 쓴다. 별도 `OAUTH_ACCOUNT` 테이블과 provider token 저장은 만들지 않는다.
- Q: 기존 USER 레코드가 있을 때 GitHub 식별자 필드를 어떻게 migration할까요? → A: 기존 USER 데이터는 보존하는 단계적 migration을 사용한다. 기존 행의 `github_user_id`는 처음에는 null을 허용하고, 값이 있는 `github_user_id`만 유일하게 관리한다. GitHub OAuth callback은 `github_user_id`로만 기존 사용자를 찾고, 이메일·이름·`github_login`으로 기존 행을 자동 연결하지 않는다. 일치하는 `github_user_id`가 없으면 새 USER를 생성하며, GitHub 식별자가 없는 기존 행의 계정 연결은 MVP 범위 밖의 명시적 후속 절차로 남긴다.
- Q: USER에 추가하는 GitHub 필드의 DB 타입·길이는 무엇으로 할까요? → A: `github_user_id`는 `BIGINT NULL`, `github_login`은 `VARCHAR(255) NULL`로 추가한다. 새 GitHub OAuth 사용자 생성 시 두 필드를 모두 채우고, 기존 행은 null을 유지한다. `github_user_id IS NOT NULL`인 행에만 유일 제약 또는 동등한 partial unique index를 적용한다. `github_login`은 유일 제약이나 사용자 식별에 사용하지 않는다.
- Q: 운영 세션 cookie의 이름은 무엇으로 할까요? → A: 인증 세션 cookie 이름은 `AGENTFIT_SESSION`으로 고정한다. 이는 `XSRF-TOKEN` CSRF cookie와 별개이며, host-only·`Path=/`·`HttpOnly`·운영 환경 `Secure`·`SameSite=Lax`·`Max-Age=7일` 정책을 적용한다.
- Q: 같은 사용자에게 여러 로그인 세션을 허용할까요? → A: Spring Security의 `maximumSessions(1)` 정책을 적용한다. 새 GitHub 로그인은 이전 서버 세션을 만료시키고, 이전 브라우저에 남은 cookie가 있어도 보호 요청은 인증되지 않는다. `maxSessionsPreventsLogin(false)`로 새 로그인을 우선한다.
- Q: 인증·프로젝트 데이터베이스 변경 이력은 어떤 방식으로 관리할까요? → A: Flyway를 사용한다. 버전 SQL migration은 `services/core-api/src/main/resources/db/migration/`에 두며, USER의 GitHub 필드 추가·partial unique index와 Spring Session JDBC 기본 schema를 같은 migration 체계에서 적용한다. 새 migration의 버전 번호와 파일명은 기존 Flyway 이력의 다음 번호를 사용한다.

### Convergence 2026-10-13

- 구현과 검증 결과를 반영해 위 Session 2026-10-08의 초기 `Path=/api` 제안은 `Path=/`로 정정한다. 실제 `SecurityConfig.java`는 `AGENTFIT_SESSION`을 host-only·`Path=/`·HttpOnly·SameSite=Lax·7일 Max-Age로 발급한다. 이 경로는 OAuth 시작·callback, 로그인 성공 redirect인 `/`, `/api` 보호 요청 모두에 동일한 세션을 전달하기 위한 것이다.
- 실제 구현은 `maximumSessions(1)`과 `maxSessionsPreventsLogin(false)`를 사용해 사용자별 활성 서버 세션을 하나로 제한한다. 새 로그인은 이전 Spring Session을 만료시키며, 이전 브라우저에 남은 cookie가 있어도 서버에서 인증되지 않는다.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 본인 프로젝트만 시작하고 찾기 (Priority: P1)

사용자는 GitHub로 식별된 뒤 자신의 개인 프로젝트를 만들고, 목록과 상세에서 자신이 소유한 프로젝트만 찾는다. 소유자는 브라우저가 보낸 값이 아니라 서버가 확인한 인증 주체로 정해진다.

**Why this priority**: 모든 A·B 기능의 데이터가 프로젝트에 묶이므로, 안전한 소유자 결정이 없으면 이후 문서 분석과 추천도 안전하지 않다.

**Independent Test**: 서로 다른 두 사용자로 프로젝트를 만들고, 각 사용자의 목록·상세가 자신의 프로젝트만 보이는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 인증된 사용자가 유효한 프로젝트 이름을 입력했을 때, **When** 프로젝트를 만들면, **Then** 새 프로젝트의 소유자는 서버가 확인한 해당 사용자로만 정해지고 공개 결과에는 소유자 식별자가 포함되지 않는다.
2. **Given** 두 사용자가 각각 프로젝트를 소유할 때, **When** 각자가 자신의 프로젝트 목록과 상세를 조회하면, **Then** 상대방 프로젝트는 나타나지 않는다.
3. **Given** 사용자가 프로젝트 이름을 비워 두거나 공백만 입력했을 때, **When** 생성을 요청하면, **Then** 프로젝트를 만들지 않고 입력을 거부한다.

---

### User Story 2 - 우회 요청에서도 타인 프로젝트를 보지 못하기 (Priority: P1)

사용자는 화면을 거치지 않고 프로젝트나 그 하위 객체 식별자를 직접 요청하더라도, 자신이 소유하지 않은 데이터의 존재·내용·변경 가능 여부를 알 수 없다.

**Why this priority**: 화면의 버튼을 숨기는 것만으로는 데이터를 보호할 수 없다. 서버가 모든 진입점에서 같은 판단을 해야 한다.

**Independent Test**: 비로그인 사용자와 비소유 사용자가 프로젝트, 초안, 추천, 미리보기, 승인 등 보호된 대상을 직접 요청하고 결과를 비교한다.

**Acceptance Scenarios**:

1. **Given** 인증되지 않은 사용자가 보호된 프로젝트 작업을 요청했을 때, **When** 서버가 요청을 검사하면, **Then** 401로 거부하고 프로젝트 데이터를 반환하지 않는다.
2. **Given** 비소유 사용자가 다른 사람의 프로젝트 식별자 또는 임의의 존재하지 않는 식별자를 요청했을 때, **When** 서버가 검사하면, **Then** 두 경우 모두 404 `PROJECT_NOT_FOUND`로 처리해 존재 여부를 구분할 수 없게 한다.
3. **Given** 하위 객체 식별자가 다른 프로젝트에 속할 때, **When** 사용자가 자신의 프로젝트 URL과 함께 그 식별자를 요청하면, **Then** 범위를 넓히지 않고 데이터를 읽거나 바꾸지 않는다.

---

### User Story 3 - 클라이언트가 소유자나 상태를 위조하지 못하기 (Priority: P2)

사용자는 프로젝트를 변경할 때 자신이 변경할 수 있는 입력만 보내며, 소유자·서버 상태·검증 결과·감사 내용을 요청으로 바꿀 수 없다. 동시에 발생한 수정은 최신 값을 조용히 덮어쓰지 않는다.

**Why this priority**: 소유권은 입력값이 아니라 서버의 보안 판단이다. 버전 충돌을 숨기면 올바른 소유자도 자신의 최신 변경을 잃을 수 있다.

**Independent Test**: 소유자 식별자·서버 상태·감사 필드를 주입한 요청과 오래된 버전의 변경 요청을 각각 실행한다.

**Acceptance Scenarios**:

1. **Given** 클라이언트가 `ownerId` 또는 서버가 생성하는 상태·감사 정보를 요청에 넣었을 때, **When** 서버가 검증하면, **Then** 해당 값을 소유권이나 상태 변경의 근거로 사용하지 않고 허용되지 않은 입력을 거부한다.
2. **Given** 같은 프로젝트를 두 요청이 서로 다른 기준 버전으로 변경하려 할 때, **When** 먼저 성공한 변경 뒤 늦은 요청이 도착하면, **Then** 늦은 요청은 저장하지 않고 충돌을 알리며, 사용자는 최신 정보를 다시 불러온다.

---

### User Story 4 - 프로젝트를 안전하게 지우기 (Priority: P2)

소유자는 명시적으로 확인한 뒤 자신의 프로젝트와 연결된 데이터를 삭제할 수 있다. 삭제 중이거나 삭제 뒤 도착한 분석 결과는 삭제된 프로젝트를 다시 만들 수 없다.

**Why this priority**: 분석·Review·추천처럼 비동기 결과가 올 수 있는 제품에서 삭제는 개인정보와 잘못된 데이터 복원을 막는 안전 장치다.

**Independent Test**: 프로젝트 삭제와 지연된 분석 결과를 의도적으로 겹치게 하고, 삭제 후 관련 데이터와 복원이 없는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 프로젝트 소유자가 명시적 삭제 확인을 보냈을 때, **When** 삭제가 성공하면, **Then** 프로젝트 및 연결된 Profile·문서 최소 메타데이터·분석 시도·Audit와 후속 연결 데이터가 함께 제거된다.
2. **Given** 삭제 확인이 없거나 거짓일 때, **When** 삭제를 요청하면, **Then** 어떤 프로젝트 데이터도 제거하지 않는다.
3. **Given** 프로젝트가 삭제된 뒤 이전 분석 또는 Review 결과가 늦게 도착했을 때, **When** 결과를 저장하려 하면, **Then** 삭제된 프로젝트나 하위 데이터를 복원하지 않고 안전하게 종료한다.
4. **Given** 인증·소유권 검증을 마치고 A6 분석 시도를 저장한 사용자가 로그아웃했을 때, **When** AI 결과가 Core API에 도착하면, **Then** 세션 유효성에 의존하지 않고 프로젝트·A6·세대·삭제 상태를 재검증해 유효한 결과만 저장하며 사용자는 재로그인 뒤 결과를 조회한다.

### Edge Cases

- A6 `PROCESSING` 저장 전 인증이 만료되거나 로그아웃하면, 새 보호 요청을 성공으로 처리하거나 기존 인증 정보를 재사용하지 않는다. 이미 인증·소유권 검증과 A6 저장을 마친 서버 소유 작업에는 이 규칙을 적용하지 않는다.
- 타인 프로젝트와 존재하지 않는 프로젝트의 응답은 상태·오류 의미·데이터 유무로 구분 가능해서는 안 된다.
- URL의 프로젝트와 하위 객체가 서로 다르면, 올바른 하위 객체라도 다른 프로젝트의 데이터에 접근할 수 없다.
- 프로젝트 삭제와 분석·Review·추천 결과 저장이 경쟁하면, 삭제가 완료된 프로젝트를 결과가 다시 만들 수 없다.
- 인증·소유권 검증과 A6 저장을 마친 분석은 사용자 로그아웃·세션 만료·브라우저 연결 종료만으로 취소되지 않는다. 다만 프로젝트 삭제·세대 불일치·A6 만료·무효 상태에서는 결과를 저장하지 않는다.
- 현재 버전과 다른 기준으로 쓰기를 시도하면, 저장하지 않고 충돌을 알린다. 사용자는 최신 정보를 다시 불러오며, 자동 병합·자동 재시도·조용한 덮어쓰기를 하지 않는다.
- 프로젝트 삭제는 사용자 계정이나 인증 세션 자체를 삭제하지 않는다.
- 프로젝트 자체 수정은 이번 범위에 포함하지 않으며, 프로젝트 생성·목록·상세·삭제와 하위 업무 데이터의 보호 쓰기만 제공한다.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-OWN-001 — 개인 소유 접근 모델**: 시스템은 프로젝트를 한 명의 개인 소유자가 접근하는 데이터 단위로 관리해야 한다. MVP에 팀 공유, 초대, 역할 기반 공동 편집 또는 소유권 이전을 자동 포함해서는 안 된다.
- **FR-OWN-002 — 인증 주체 식별**: 시스템은 Spring Boot의 OAuth2 로그인과 PostgreSQL 기반 Spring Session으로 사용자를 인증해야 한다. 기존 `USER`에는 GitHub의 변경되지 않는 숫자 ID인 `github_user_id BIGINT NULL`과, 표시·로그용으로만 쓰는 변경 가능한 `github_login VARCHAR(255) NULL`을 둔다. 새 GitHub OAuth 사용자 생성 시 두 필드를 모두 채우며, 기존 USER를 보존하기 위해 Flyway migration으로 null을 허용한다. `github_user_id IS NOT NULL`인 값만 유일해야 하고 `github_login`에는 유일 제약을 두지 않는다. OAuth callback은 `github_user_id`로만 기존 사용자를 찾으며 이메일·이름·`github_login`으로 자동 연결해서는 안 된다. 일치하는 ID가 없으면 새 USER를 만들고, GitHub 식별자가 없는 기존 USER의 연결은 MVP 범위 밖이다. 별도 `OAUTH_ACCOUNT` 테이블과 provider token 저장은 만들지 않는다. Flyway 버전 SQL은 `services/core-api/src/main/resources/db/migration/`에서 USER 확장·partial unique index와 Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` schema를 적용한다. `PRINCIPAL_NAME`에는 AgentFit 내부 사용자 ID를 저장하며, 별도 AgentFit `SESSION` 테이블은 만들지 않는다. 인증 세션 cookie 이름은 `AGENTFIT_SESSION`이며, OAuth 시작은 `GET /api/oauth2/authorization/github`, GitHub callback은 `GET /api/login/oauth2/code/github`으로 고정하고 프록시와 GitHub OAuth App redirect URI도 이 callback을 사용해야 한다. 같은 Origin의 `/api` 경로에서 host-only, `Path=/api`, `HttpOnly`, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age=7일`인 지속 세션 쿠키를 통해 프로젝트 작업의 사용자 주체를 식별해야 한다. 서버 세션은 마지막 인증된 요청 후 7일간 활동이 없으면 만료되고, 지속 쿠키는 발급 뒤 7일에 만료되며 자동 연장하지 않는다. 쿠키 또는 서버 세션 중 먼저 만료되거나 로그아웃하면 Spring Session JDBC 서버 세션과 브라우저 쿠키를 폐기하고 다시 로그인해야 한다. `POST /api/auth/logout`은 CSRF 검증 뒤 이 폐기를 수행해야 한다. 개발 환경 `localhost`만 `Secure`를 끌 수 있고, 같은 Origin이므로 CORS credential 설정은 두지 않는다. `GET /api/auth/csrf`는 JavaScript가 읽을 수 있도록 `Path=/`, 비-HttpOnly, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age` 없는 세션성 `XSRF-TOKEN`을 제공하고, 모든 `POST`·`PUT`·`PATCH`·`DELETE /api/**` 요청은 같은 값을 `X-XSRF-TOKEN` 헤더에 포함해야 한다. 누락·불일치는 `403 CSRF_INVALID`로 거부해야 한다. GitHub access token·refresh token은 로그인 중 사용자 식별·이메일 조회에만 사용한 뒤 즉시 폐기하며 DB·Spring Session JDBC 테이블·Audit·일반 로그에 저장하지 않아야 한다. Bearer/JWT 본문과 GitHub access token/id token을 프로젝트 API 인증 토큰으로 사용하거나 브라우저에 노출해서는 안 된다. OAuth scope는 `user:email`로 제한하고 저장소 권한·임의 provider·임의 scope를 허용하지 않는다. 로그인 취소·실패·세션 만료를 로그인 성공으로 취급해서는 안 된다.
- **FR-OWN-003 — 서버 소유자 결정**: 프로젝트 생성 시 소유자는 서버가 확인한 인증 주체로만 결정해야 한다. 클라이언트가 보낸 소유자 값, 식별자 형식 또는 추측한 권한 정보를 근거로 소유자를 정해서는 안 된다.
- **FR-OWN-004 — 소유 프로젝트 기본 작업**: 인증된 사용자는 자신의 프로젝트를 생성·목록 조회·상세 조회할 수 있어야 한다. 빈 문자열 또는 공백뿐인 이름으로는 프로젝트를 만들 수 없어야 한다.
- **FR-OWN-005 — 모든 보호 작업의 소유권 검사**: 시스템은 조회·입력·분석·수정·삭제를 포함한 모든 보호 작업마다 인증 주체와 프로젝트 소유권을 서버에서 검증해야 한다. 이 검사는 화면 흐름을 우회한 직접 요청에도 동일하게 적용되어야 한다.
- **FR-OWN-006 — 존재 정보 은닉**: 인증되지 않은 보호 요청은 401로 거부해야 한다. 인증됐지만 소유하지 않은 프로젝트와 존재하지 않는 프로젝트는 모두 404 `PROJECT_NOT_FOUND`로 처리하고, 프로젝트의 존재·내용·수정 가능 여부를 노출해서는 안 된다.
- **FR-OWN-007 — 하위 리소스 프로젝트 격리**: 초안, 추천, 미리보기, 승인 및 이후 추가되는 프로젝트 하위 객체는 요청의 프로젝트에 실제로 속할 때만 접근·변경할 수 있어야 한다. 다른 프로젝트의 하위 객체 식별자를 사용해 접근 범위를 넓힐 수 없어야 한다.
- **FR-OWN-008 — 공개 정보와 입력 경계**: 공개 프로젝트 결과는 `ownerId`를 노출해서는 안 된다. 요청은 허용된 사용자 입력만 받아야 하며, `ownerId`, 서버 생성 상태, 검증 성공 여부, 출처, Audit 내용을 주입하는 요청을 거부해야 한다. 식별자 형식만으로 소유자·정렬·권한을 추론할 수 없어야 한다.
- **FR-OWN-009 — 버전 충돌과 쓰기 순서**: Profile·분석·하위 업무 데이터처럼 실제로 제공되는 보호 쓰기는 현재 소유권과 기준 버전을 확인하고 프로젝트 단위의 일관된 쓰기 순서를 따라야 한다. 프로젝트 자체 수정 endpoint는 이 범위에 포함하지 않는다. 기준이 오래되거나 경쟁이 발생하면 시스템은 변경을 저장하지 않고 충돌을 알려야 하며, 사용자가 최신 정보를 다시 불러오게 해야 한다. 기존 값을 자동 병합·자동 치환·조용한 덮어쓰기로 바꾸어서는 안 된다.
- **FR-OWN-010 — 소유자 확인 삭제와 연결 데이터 정리**: 프로젝트 삭제는 소유자만 명시적으로 확인하고 현재 `expectedVersion`을 함께 보낸 경우에만 수행해야 한다. Core API는 `PROJECT` 잠금 뒤 현재 version이 일치할 때만 삭제하고, 오래된 version은 어떤 데이터도 삭제하지 않은 채 `409 VERSION_CONFLICT`로 거부해야 한다. 프로젝트에 종속된 A·B 업무 데이터(Profile·문서 최소 메타데이터·분석 시도·Review·추천·설정·Export·Audit 등)는 한 작업으로 정리해야 한다. 공용 Catalog, 사용자 계정, 인증 Session, 사용자 PC에 이미 다운로드된 파일은 삭제하지 않는다.
- **FR-OWN-011 — 삭제 이후 지연 결과 차단과 AI 호출 경계**: 실행 중인 분석·Review·추천 작업을 MVP에서 강제 취소하지 않는다. 분석 시작 시 PDF A6 `ANALYSIS_ATTEMPT`를 먼저 만들고 커밋한 뒤, Core API가 브라우저 HTTP 연결·사용자 세션과 독립된 서버 소유 작업으로 `POST /internal/v1/analysis`를 호출한다. 사용자 로그아웃·세션 만료·브라우저 연결 종료는 이미 접수된 작업을 취소하지 않으며 새 요청과 결과 조회만 막는다. Spring Boot는 `X-AgentFit-Key-Id`, `X-AgentFit-Timestamp`, `X-AgentFit-Signature`를 포함하고 HMAC-SHA256으로 HTTP method·path·timestamp·`requestId`·`generation`·`contractVersion`·실제 전송 UTF-8 body의 SHA-256 소문자 hex를 줄바꿈 순서대로 서명해 FastAPI에 보낸다. 서명은 padding 없는 base64url이며, 두 서비스는 current key와 만료 전 previous key를 배포 secret 설정에서 읽는다. `AGENTFIT_HMAC_CURRENT_KEY_ID`와 `AGENTFIT_HMAC_CURRENT_SECRET`은 함께 필수이고, previous key를 쓰면 `AGENTFIT_HMAC_PREVIOUS_KEY_ID`·`AGENTFIT_HMAC_PREVIOUS_SECRET`·`AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL` 세 값을 모두 제공해야 한다. current 쌍이 없거나 previous 값이 일부만 있으면 시작에 실패하며, previous key는 valid-until 전까지만 허용한다. FastAPI는 HMAC·timestamp·요청 context만 검증하며, 세션·사용자 권한·프로젝트 소유권·PostgreSQL·최종 확정 상태·DB 저장을 소유하지 않는다. AI Service가 처리 접수를 확정한 같은 `requestId`·body hash는 10분간 중복 처리하지 않되, 아직 처리하지 않았음을 명시한 `503`에는 fingerprint를 남기지 않아 Core API가 최대 1회 재시도할 수 있다. 재시작 뒤 중복 결과의 최종 저장 차단은 Core API의 A6 재검증이 담당한다. 이번 기능의 유효 요청 응답은 `result.kind = CONTRACT_TEST_DOUBLE`인 계약 테스트 대역이며, Core API는 이를 DRAFT·추천·확정 업무 데이터로 변환하지 않는다. 요청 전송 후 timeout은 자동 재시도하지 않고 A6의 `PROCESSING`과 `deadline_at`을 따른다. 결과를 저장하기 직전에 Spring Boot는 프로젝트 존재·삭제 상태·소유권·세대·A6 상태·기한·DRAFT 기준을 다시 확인하며, 삭제되었거나 더 이상 유효하지 않은 결과는 저장·복원·변경하지 않는다. HMAC 검증 실패는 `401 INTERNAL_AUTH_FAILED`로 즉시 실패 처리하고 HMAC secret·서명값은 업무 데이터나 Audit에 저장하지 않는다.
- **FR-OWN-012 — 최소 Audit과 안전한 실패 기록**: 프로젝트가 살아 있는 동안 중요 보호 작업의 행위자·대상·시점·결과를 추적할 수 있어야 한다. 성공 상태 변경과 해당 Audit은 함께 반영되어야 하며, 거부·실패 기록에는 원문·Secret·원시 AI 응답을 넣어서는 안 된다.
- **FR-OWN-013 — 계약과 실제 동작의 일치**: Frontend가 의존하는 `/api/oauth2/authorization/github`, `/api/login/oauth2/code/github`, `/api/auth/csrf`, `/api/auth/logout`, `/api/auth/get-session`, `/api/projects`, `/api/projects/{projectId}` 경로와 소유권·오류·공개 정보·CSRF 규칙은 공유 계약, 서버 검증, 정상·실패 검증 시나리오에서 같은 의미여야 한다. Core API–AI Service의 `/internal/v1/analysis` 경로·HMAC header·canonical 문자열·오류 의미도 `contracts/openapi/ai-service.yml`과 동일해야 한다. DTO·오류 본문·한도·삭제 확인 wire 형식은 승인된 계약 버전과 일치해야 하며, 문서에만 있는 경로나 DTO를 구현 완료로 간주해서는 안 된다.

### Requirement Acceptance Criteria

| Requirement | 확인 기준 |
| --- | --- |
| FR-OWN-001 | 두 소유자가 동시에 존재해도 서로의 프로젝트를 목록·상세·하위 객체에서 볼 수 없고, 팀 공유 기능이 활성화되지 않는다. |
| FR-OWN-002 | `/api/oauth2/authorization/github`에서 시작한 GitHub OAuth가 `/api/login/oauth2/code/github` callback으로 돌아온 뒤 Spring Boot가 `github_user_id BIGINT`로만 기존 USER를 찾고, 없으면 `github_user_id BIGINT`·`github_login VARCHAR(255)`를 모두 채운 새 USER를 만든다. 이메일·이름·`github_login`에 의한 자동 계정 연결은 발생하지 않는다. 기존 USER의 null `github_user_id`는 보존하며, null이 아닌 `github_user_id`만 유일하고 `github_login`은 유일하지 않다. Spring Boot는 PostgreSQL Spring Session JDBC의 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`에 세션을 저장한 뒤 이름이 `AGENTFIT_SESSION`인 `Max-Age=7일` HttpOnly 세션 쿠키를 발급한다. 별도 `OAUTH_ACCOUNT`·AgentFit `SESSION` 테이블은 만들지 않는다. 성공 callback은 정확히 `/?login=success`, 실패·취소 callback은 정확히 `/?login=failed`로 이동하며 redirect URL에 provider `error`·`code`·`state`·token이 없다. 실패·취소 callback은 세션 쿠키를 발급하지 않는다. 마지막 인증된 요청 후 7일 동안 활동이 없거나 쿠키 발급 뒤 7일이 지나거나 로그아웃한 세션은 보호 작업을 통과하지 않는다. `GET /api/auth/csrf`가 제공한 token 없이 상태 변경 요청을 보내면 403 `CSRF_INVALID`이며, token이 일치하면 정상 처리한다. GitHub access token·refresh token은 영속 저장하지 않으며, 유효한 세션 쿠키만 보호 작업을 통과한다. Bearer/JWT 본문·GitHub access token/id token·저장소 권한·임의 scope는 성공으로 처리되지 않는다. |
| FR-OWN-003 | 서로 다른 사용자가 같은 생성 입력을 보내도 서버가 확인한 각 인증 주체가 각각의 소유자가 되며, 요청의 소유자 주입은 영향을 주지 않는다. |
| FR-OWN-004 | 소유자는 자신의 프로젝트 생성·목록·상세를 완료하고, 빈/공백 이름 생성은 프로젝트를 만들지 않는다. |
| FR-OWN-005 | 조회·입력·분석·수정·삭제의 보호 작업 표본에서 비로그인·비소유 요청의 데이터 조회·변경 성공은 0건이다. |
| FR-OWN-006 | 비로그인 요청은 401이고, 타인·없는 프로젝트의 404 응답은 `PROJECT_NOT_FOUND` 및 데이터 미반환으로 동일하다. |
| FR-OWN-007 | 다른 프로젝트의 초안·추천·미리보기·승인 식별자를 조합한 요청은 어느 쪽의 데이터도 반환·변경하지 않는다. |
| FR-OWN-008 | 공개 프로젝트 결과에 `ownerId`가 없고, 소유자·서버 상태·Audit 주입 요청은 거부되며, 식별자 형식으로 권한을 알아낼 수 없다. |
| FR-OWN-009 | 오래된 기준의 두 번째 쓰기는 저장되지 않고 첫 번째 성공 값을 보존한다. 사용자에게 충돌이 알려지고 최신 정보를 다시 불러올 수 있으며, 자동 병합·자동 치환·조용한 성공은 발생하지 않는다. |
| FR-OWN-010 | 본인·명시 확인·현재 `expectedVersion` 삭제만 성공하며, 오래된 version 삭제는 409이고 어떤 프로젝트 데이터도 삭제하지 않는다. 성공 뒤 프로젝트 종속 A·B 데이터는 남지 않으며 공용 Catalog·USER/SESSION·사용자 PC 파일은 삭제되지 않는다. |
| FR-OWN-011 | A6를 저장한 뒤 사용자가 로그아웃·세션 만료·브라우저 연결 종료를 겪어도 서버 소유 작업은 계속 처리하고, `/internal/v1/analysis`는 승인된 HMAC header·canonical 문자열·timestamp 정책을 통과한 요청만 처리한다. 프로젝트·A6·세대·삭제 상태 재검증을 통과한 결과만 저장하며, 삭제·세대 불일치·만료·무효 작업의 결과는 저장·복원·변경되지 않는다. |
| FR-OWN-012 | 성공 상태 변경에는 관련 Audit이 있고, 거부·실패 기록에는 원문·Secret·원시 AI 응답이 없다. 프로젝트 삭제 뒤에는 프로젝트 종속 Audit도 함께 제거된다. |
| FR-OWN-013 | 계약의 정상·비로그인·비소유·없는 프로젝트·버전 충돌 예시와 실제 검증 결과가 같은 공개 필드와 오류 의미를 보인다. |

### Key Entities *(include if feature involves data)*

- **인증 주체**: GitHub OAuth로 인증된 사용자이며, null이 아닌 기존 USER의 유일 `github_user_id BIGINT`로 식별하고 `github_login VARCHAR(255)`은 표시·로그용으로만 사용한다. 기존 USER의 null `github_user_id`는 migration 중 보존하며 자동 계정 연결 대상이 아니다. Spring Boot가 PostgreSQL Spring Session JDBC의 `SPRING_SESSION`과 `SPRING_SESSION_ATTRIBUTES`에 저장한 세션의 `PRINCIPAL_NAME`(AgentFit 내부 userId)·마지막 활동 시각·만료 시각으로 Core API가 현재 주체를 확인한다. 브라우저는 이름이 `AGENTFIT_SESSION`인 `Max-Age=7일` HttpOnly 세션 쿠키만 보유하고 프로젝트 소유자를 결정하는 근거는 서버가 조회한 세션 사용자다.
- **프로젝트**: 한 인증 주체가 소유하는 업무 데이터의 최상위 경계다. 이름, 현재 단계, 변경 감지용 버전, 분석·Review 세대 정보를 가질 수 있지만 공개 결과에 소유자 식별자를 포함하지 않는다.
- **프로젝트 하위 리소스**: 문서 요약, 분석 시도, 기획 초안·확정본, 추천, 미리보기, 승인처럼 반드시 하나의 프로젝트에 귀속되는 데이터다.
- **Audit 이벤트**: 프로젝트가 살아 있는 동안 중요한 상태 변화·거부·실패의 최소 추적 정보를 보관하는 기록이다. 프로젝트 삭제 시 프로젝트 종속 Audit도 함께 정리된다.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-OWN-001**: 비로그인과 비소유 사용자가 보호된 프로젝트·하위 객체에 직접 요청하는 인수 시나리오에서 타인 데이터 조회·변경 성공은 0건이다.
- **SC-OWN-002**: 소유자와 비소유자, 존재하는 프로젝트와 없는 프로젝트를 비교하는 모든 지정 시나리오에서 타인·없는 프로젝트 응답은 같은 404 `PROJECT_NOT_FOUND`이며 프로젝트 데이터는 0건이다.
- **SC-OWN-003**: 소유자 주입, 서버 상태 주입, 하위 객체 교차 참조, 오래된 버전의 쓰기를 포함한 지정된 위조·경쟁 시나리오에서 무단 변경과 조용한 덮어쓰기는 0건이다.
- **SC-OWN-004**: 프로젝트 삭제와 지연 결과 저장을 겹친 지정된 시나리오마다 삭제된 프로젝트 및 연결 데이터의 복원은 0건이고, 사용자 계정·세션 삭제는 0건이다.
- **SC-OWN-005**: 이 기능이 공개하는 보호 작업마다 정상, 비로그인, 비소유/없는 프로젝트, 입력 위조, 버전 충돌, 삭제 후 지연 결과, 로그아웃·세션 만료·브라우저 연결 종료 뒤 AI 결과 저장의 인수 시나리오가 최소 1개씩 존재한다.

## Assumptions

- 이 명세의 데이터 접근 단위는 개인 소유 프로젝트다. 팀 공유, 초대, 공동 편집, 소유권 이전은 별도 기능이 승인되기 전까지 범위 밖이다.
- GitHub는 유일한 로그인 provider이며 OAuth scope는 `user:email`로 제한한다. 기존 USER에는 `github_user_id BIGINT NULL`과 표시·로그용 `github_login VARCHAR(255) NULL`을 직접 저장하고, 기존 행의 null `github_user_id`를 보존한다. null이 아닌 `github_user_id`만 유일하며, OAuth callback은 그 값으로만 기존 사용자를 찾는다. 이메일·이름·`github_login`으로 자동 연결하지 않고 일치하는 ID가 없으면 두 GitHub 필드를 채운 새 USER를 만든다. 별도 `OAUTH_ACCOUNT` 테이블이나 provider token 저장은 두지 않는다. 정적 Frontend와 Spring Boot Core API는 같은 Origin으로 제공하고 Core API는 `/api` 경로로 프록시한다. OAuth 시작·callback은 각각 `/api/oauth2/authorization/github`, `/api/login/oauth2/code/github`이며, GitHub OAuth App redirect URI와 reverse proxy도 callback 경로를 사용한다. 성공·실패 callback은 각각 `/?login=success`, `/?login=failed`로 이동하며 provider 오류 원문·token·code·state를 노출하지 않는다. Spring Boot가 OAuth callback·token 교환과 PostgreSQL Spring Session JDBC의 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` 관리를 담당하고 `PRINCIPAL_NAME`으로 내부 userId를 연결한다. 별도 AgentFit `SESSION` 테이블은 만들지 않는다. Core API는 이름이 `AGENTFIT_SESSION`인 세션 쿠키가 포함된 요청에서 해당 Spring Session을 조회해 인증 주체를 얻으며, 마지막 인증된 요청 후 7일간 활동이 없으면 세션을 만료한다. 세션 쿠키는 host-only, `Path=/api`, `HttpOnly`, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age=7일`의 지속 쿠키이며 자동 연장하지 않는다. 개발 환경 `localhost`만 `Secure`를 끌 수 있고 CORS credential 설정은 사용하지 않는다. `GET /api/auth/csrf`는 `Path=/`·비-HttpOnly의 읽기 가능한 `XSRF-TOKEN`을 발급하며, 상태 변경 요청은 `X-XSRF-TOKEN`으로 검증한다. GitHub provider access/refresh token은 로그인 중 사용자 식별 뒤 즉시 폐기하고 저장·갱신하지 않는다.
- A는 서버에서 인증 주체와 프로젝트 소유자를 연결하고 모든 보호 작업을 검증한다. Frontend는 로그인·오류·재확인 화면을 제공할 수 있지만 서버 소유권 검증을 대체할 수 없다.
- 문서 분석, Profile 확정, Review, 개발자·환경 정보, B의 추천·설정은 이 기능에서 구현하지 않는다. 이 기능은 이후 해당 작업들이 사용할 소유권·프로젝트 격리 규칙을 정한다.
- 실제 LLM 모델 처리 규칙은 별도 기능에서 구체화한다. 이번 기능의 `/internal/v1/analysis`는 HMAC 전송·검증과 `CONTRACT_TEST_DOUBLE` 결과만 제공하며, Core API는 결과 저장 보호 경계만 검증한다. 사용자 세션은 분석 접수 시 인증·소유권 확인에만 쓰고, 결과 저장은 A6·프로젝트 재검증으로 판단한다. 서버 재시작·작업자 중단으로 결과를 보장하지 못하면 A6 deadline에 따라 성공으로 처리하지 않고 실패로 종료한다.
- 물리 삭제 그래프와 PostgreSQL 경쟁 검증은 전용 PostgreSQL 환경에서 실행하며, Core API AI client·결과 저장 재검증·AI Service HMAC contract test-double은 저장소에 구현되어 있다. 전체 요구사항 완료 여부는 `tasks.md` 체크 상태와 실제 테스트 결과로 판정한다.

## Source References and Open Issues

- 최신 통합 개발기준은 A의 `identity / project` 책임을 “인증·소유권·프로젝트·공통 DB 통합”으로, A1 `PROJECT`를 “프로젝트 소유권 / 현재 단계 / 공통 버전”으로 정의한다. 같은 프로젝트의 쓰기는 프로젝트를 먼저 잠그며, 삭제는 프로젝트 잠금·인증·소유권·명시적 확인을 한 트랜잭션에서 처리한다. (`Docs/AgentFit_AB_1차통합_개발기준.pdf` p.4, p.6, p.9, p.35)
- 사용자가 제공한 세부 근거는 개인 소유 프로젝트와 GitHub 로그인, 401/404 은닉 규칙, 하위 객체 귀속, `ownerId` 비공개, 삭제 범위를 정의한다. (`C:/dev_source/project_manager/Docs/PRD.md` L62, L143-L146, L366; `Docs/api/common.md` L10-L17; `Docs/api/01-project-analysis.md` L30, L192; `Docs/api/openapi.phase1.json` L2098)
- 충돌·누락: 최신 PDF는 구체 인증 schema, endpoint, 공개 DTO를 확정하지 않았고, 제공된 API 문서는 설계·실제 호출 검증 전이라고 표시한다. 이 명세는 사용자가 요청한 세부 동작을 인수 대상으로 채택하지만, 실제 API 계약을 변경하기 전에는 최신 기준·OpenAPI·fixture를 함께 합의해야 한다.
- 추가 결정: 인증 주체의 내부 전달 방식은 PostgreSQL 기반 Spring Boot 서버 세션 쿠키로 채택했다. 브라우저에는 HttpOnly 쿠키만 전달하고, Core API는 PostgreSQL Spring Session JDBC 기본 테이블을 조회한다. 별도 AgentFit `SESSION` 테이블과 Refresh Token 발급·갱신 API는 이 기능에 포함하지 않는다.
- 추가 결정: 서버 세션은 마지막 인증된 요청 뒤 7일 비활성 시 만료한다. 브라우저 쿠키는 `Max-Age=7일`의 지속 HttpOnly 쿠키로 발급하며 자동 연장하지 않는다. 둘 중 먼저 만료되거나 로그아웃하면 DB 세션과 쿠키를 즉시 폐기하고 GitHub OAuth 로그인을 다시 진행한다.
- 추가 결정: 정적 Frontend와 Core API는 같은 Origin으로 제공하며 API 경로는 `/api`다. 세션 쿠키는 host-only, `Path=/api`, `HttpOnly`, 운영 환경 `Secure`, `SameSite=Lax`로 발급하고 CORS credential 설정은 사용하지 않는다. 상태 변경 요청은 Spring Security CSRF 보호를 유지한다.
- 추가 결정: OAuth 시작·callback은 각각 `/api/oauth2/authorization/github`, `/api/login/oauth2/code/github`으로 고정하고 GitHub redirect URI와 프록시 경로를 동일하게 맞춘다. `GET /api/auth/csrf`의 `XSRF-TOKEN`과 상태 변경 요청의 `X-XSRF-TOKEN`으로 CSRF를 검증하며, 실패는 `403 CSRF_INVALID`로 처리한다.
- 추가 결정: GitHub provider access token·refresh token은 로그인 중 식별에만 사용하고 영속 저장하지 않는다. DB·SESSION·Audit·일반 로그에 넣지 않으며 provider token 갱신 흐름은 이번 기능 범위에 포함하지 않는다.
- 추가 결정: Spring Boot와 FastAPI 사이의 내부 요청은 HMAC으로 인증한다. FastAPI는 HMAC·요청 유효성만 검증하고, 사용자 권한·프로젝트 소유권·최종 확정 상태는 Spring Boot가 소유한다.
- 추가 결정: AI 내부 경로는 `POST /internal/v1/analysis`로 고정한다. `X-AgentFit-Key-Id`·`X-AgentFit-Timestamp`·`X-AgentFit-Signature`와 method·path·timestamp·A6 context·body hash canonical 문자열을 사용하며, FastAPI는 같은 request ID·body hash를 10분간 중복 처리하지 않는다. 재시작 뒤 중복 결과는 Core API의 A6 저장 재검증으로 차단한다.
- 추가 결정: Redis 없이 AI 분석 요청을 추적할 때는 PDF A6 `ANALYSIS_ATTEMPT`를 사용한다. A6는 분석 시작 전에 생성하며 `request_id`·`generation`·상태·기한을 HMAC context와 결과 저장 검증에 연결한다. 별도 `AI_REQUEST` 테이블은 만들지 않는다.
- 추가 결정: A6를 저장해 접수한 AI 작업은 사용자 로그아웃·세션 만료·브라우저 연결 종료와 독립된 Core API 소유 작업으로 진행한다. 결과는 프로젝트·A6·세대·삭제 상태를 재검증한 뒤에만 저장하고, 사용자는 재로그인 후 결과를 조회한다.
- 추가 결정: 삭제는 프로젝트 종속 A·B 업무 데이터에만 적용한다. 공용 Catalog·계정·Session·사용자 PC 파일은 보존하며, 실행 중 작업은 취소하지 않고 삭제 상태·세대·존재 재검증으로 늦은 결과를 차단한다.
