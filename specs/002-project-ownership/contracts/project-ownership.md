# 프로젝트 소유권 계약 제약과 Fixture

**Status**: 인증·프로젝트·AI 호출의 공유 계약을 반영한다. 구현·실제 호출 검증과는 구분하며, OpenAPI와 fixture가 구현의 기준이다.
**Consumers**: Frontend, Core API, 이후 프로젝트 하위 기능(A·B), AI 결과 저장 경계

## 1. 공통 접근 규칙

| 상황 | 계약상 결과 | 반환해서는 안 되는 것 |
| --- | --- | --- |
| 인증 주체가 없음·만료됨 | 401, 보호 데이터 없음 | 프로젝트·하위 리소스·소유자 정보 |
| 인증됐으나 타인 프로젝트 | 존재하지 않는 프로젝트와 같은 404 PROJECT_NOT_FOUND | 존재 여부·이름·버전·하위 객체 정보 |
| 존재하지 않는 프로젝트 | 타인 프로젝트와 같은 404 PROJECT_NOT_FOUND | 존재 여부를 구분할 단서 |
| URL 프로젝트와 하위 객체가 불일치 | 접근·변경 거부, 데이터 없음 | 다른 프로젝트 하위 객체의 내용 |
| 소유자와 유효한 기준 버전 | 정상 결과 | 공개 결과의 ownerId |

## 2. 입력·공개 정보 제약

- 소유자는 서버가 확인한 인증 주체로만 결정한다.
- 요청은 허용된 사용자 입력만 받는다. ownerId, 서버 생성 상태, 검증 성공 여부, 출처, Audit 내용의 주입을 거부한다.
- 프로젝트 식별자는 불투명하다. 형식·순서·값만으로 소유자나 권한을 유추할 수 없다.
- 프로젝트 공개 결과에는 ownerId를 포함하지 않는다.
- GitHub는 사용자 식별에만 사용하며 저장소 권한을 요청하지 않는다. GitHub provider access/refresh token은 로그인 중 식별에만 사용한 뒤 저장하지 않는다. OAuth와 세션 전달 형식은 이 계약의 아래 인증·세션 규칙을 따른다.

## 3. Spring Boot 세션 기반 인증 주체 전달

- GitHub OAuth 시작은 `GET /api/oauth2/authorization/github`, callback·token 교환은 `GET /api/login/oauth2/code/github`에서 Spring Boot가 처리한다. GitHub OAuth App redirect URI와 reverse proxy도 callback 경로를 사용한다. provider는 GitHub 하나만 사용하며 scope는 `user:email`로 제한한다.
- Spring Boot는 기존 USER와 PostgreSQL 기반 Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`를 관리하고 브라우저에는 이름이 `AGENTFIT_SESSION`인 `Max-Age=7일` 지속 HttpOnly 세션 쿠키만 발급한다.
- 정적 Frontend와 Core API는 같은 Origin에서 제공하고 Core API는 `/api` 경로로 프록시한다. `AGENTFIT_SESSION`은 host-only, `Path=/api`, 운영 환경 `Secure`, `SameSite=Lax`로 발급한다. 개발 환경 `localhost`만 `Secure`를 끌 수 있으며, 같은 Origin이므로 CORS credential 설정을 두지 않는다.
- 기존 `USER` 테이블을 서비스 사용자 기준으로 재사용한다. `github_user_id BIGINT NULL`과 `github_login VARCHAR(255) NULL`을 직접 추가하고, null이 아닌 `github_user_id`만 유일하게 관리한다. callback은 `github_user_id`로만 기존 USER를 찾고, 없으면 두 GitHub 필드를 채운 새 USER를 만든다. 이메일·이름·`github_login`으로 기존 USER를 자동 연결하지 않는다. `OAUTH_ACCOUNT`·별도 AgentFit `SESSION`·`VERIFICATION` 테이블은 만들지 않으며 provider access/refresh token은 저장하지 않는다.
- Core API는 매 요청 `AGENTFIT_SESSION` cookie의 세션 토큰으로 PostgreSQL Spring Session JDBC의 `SPRING_SESSION`을 조회해 존재·만료·폐기 여부와 `PRINCIPAL_NAME` 내부 userId 연결을 확인한 뒤 인증 주체로 사용한다.
- 서버 세션은 마지막 인증된 요청 뒤 7일간 활동이 없으면 만료한다. 지속 쿠키는 발급 뒤 7일에 만료되며 자동 연장하지 않는다. 쿠키·서버 세션 중 먼저 만료되거나 로그아웃하면 Spring Session JDBC 데이터와 브라우저 쿠키를 함께 즉시 폐기한다.
- Bearer 인증, JWT 본문, GitHub access token/id token을 프로젝트 API 인증에 사용하거나 브라우저에 노출하지 않는다.
- `GET /api/auth/csrf`는 JavaScript가 읽을 수 있도록 `Path=/`, 비-HttpOnly, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age` 없는 세션성 `XSRF-TOKEN`을 발급한다. `POST`·`PUT`·`PATCH`·`DELETE /api/**` 요청은 같은 값을 `X-XSRF-TOKEN` 헤더로 보내야 하며, 누락·불일치는 `403 CSRF_INVALID`다. 이 CSRF 쿠키는 세션 쿠키가 아니다.
- `POST /api/auth/logout`은 유효한 CSRF token을 요구하며, 성공 시 Spring Session JDBC 서버 세션을 폐기하고 `AGENTFIT_SESSION` cookie를 만료시킨 뒤 `204`를 반환한다.
- 클라이언트가 보내는 userId·ownerId는 세션의 userId보다 우선하거나 소유권 근거로 사용할 수 없다.
- Refresh Token 발급·갱신 API는 이 기능의 계약에 포함하지 않으며, 세션 만료 시 다시 GitHub 로그인을 진행한다.

## 4. Spring Boot–FastAPI 내부 호출 제약

이 기능에서 AI 모델 실행 자체는 범위 밖이지만, A6 분석 시도와 결과 저장 경계는 소유권 안전성 검증을 위해 포함한다.

- Spring Boot는 `POST /internal/v1/analysis`의 body에 A6 내부 `request_id`·`contract_version` 값을 각각 `requestId`·`contractVersion`으로, `generation`과 AI 입력을 함께 포함한다. 사용자 ID·브라우저 세션 cookie·Bearer/JWT·GitHub token은 보내지 않는다.
- 인증·소유권 검증을 마치고 A6를 `PROCESSING`으로 커밋한 뒤의 AI 실행은 브라우저 HTTP 연결·사용자 서버 세션과 독립된 Core API 소유 작업이다. 로그아웃·세션 만료·연결 종료는 새 요청과 결과 조회만 막으며, 이미 접수된 작업은 취소하지 않는다.
- FastAPI는 HMAC·timestamp·요청 context만 검증하고 PostgreSQL·Spring Session JDBC 테이블·PROJECT·A6를 조회하지 않는다.
- 결과 저장·재시도·중복 판단은 Spring Boot가 A6와 PROJECT 잠금으로 수행한다. 결과 반환 시에는 프로젝트·A6·세대·삭제 상태·deadline을 재검증하고, 유효한 결과만 DB에 저장한다. 브라우저 연결이 끊긴 사용자는 재로그인 후 저장된 결과를 조회한다.
- 연결 전 실패·명확한 일시적 5xx만 같은 요청 context로 최대 1회 재시도하고, 전송 후 timeout은 자동 재시도하지 않는다.
- HMAC-SHA256의 canonical 문자열은 `HTTP method`, `path`, `timestamp`, `requestId`, `generation`, `contractVersion`, 실제 전송 UTF-8 body의 SHA-256 소문자 hex를 이 순서로 줄바꿈해 구성한다. header는 `X-AgentFit-Key-Id`, `X-AgentFit-Timestamp`, `X-AgentFit-Signature`를 사용하며 signature는 padding 없는 HMAC-SHA256 결과의 base64url 인코딩이다. timestamp 허용 범위는 서버 시간 기준 ±5분이고, `keyId`로 current key와 만료 전 previous key를 함께 허용한다.
- 두 서비스는 배포 secret 설정의 `AGENTFIT_HMAC_CURRENT_KEY_ID`, `AGENTFIT_HMAC_CURRENT_SECRET`을 필수로 읽으며, 하나라도 없으면 시작에 실패한다. `AGENTFIT_HMAC_PREVIOUS_KEY_ID`, `AGENTFIT_HMAC_PREVIOUS_SECRET`, `AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL`은 선택이지만 previous key를 설정하면 세 값 모두 필요하고 기한 뒤 즉시 거부한다. secret·서명값·원문 body는 로그·Audit·업무 DB에 저장하지 않는다.
- FastAPI는 처리 접수를 확정한 같은 `requestId`와 body hash 조합을 10분간 중복 처리하지 않는다. 아직 처리하지 않았음을 보장하는 일시적 `503`에는 fingerprint를 남기지 않으므로 Core API가 같은 context로 최대 1회 재시도할 수 있다. timeout 또는 처리 여부가 불명확한 5xx는 재시도하지 않는다. 프로세스 재시작으로 이 보호가 사라질 수 있으므로, 중복 결과의 최종 저장 차단은 Spring Boot의 A6·PROJECT 재검증이 담당한다. HMAC·timestamp·중복 요청 오류는 모두 `401 INTERNAL_AUTH_FAILED`로 응답하며 실패 사유를 세분화해 노출하지 않는다.
- 이번 기능에서 유효한 내부 요청은 `result.kind = CONTRACT_TEST_DOUBLE`만 반환한다. Core API는 이 값을 DRAFT·추천·확정 업무 데이터로 변환하지 않고, A6·프로젝트의 결과 저장 가능 여부만 검증한다. 실제 LLM 입력·결과 schema는 다음 AI 기능의 새 계약 버전에서 정한다.

## 5. 프로젝트·세션 API 계약

### 인증·세션·CSRF

- `GET /api/oauth2/authorization/github`은 GitHub authorization endpoint로 `302` 이동한다.
- `GET /api/login/oauth2/code/github`은 OAuth `code`·`state`를 처리하고, 성공하면 `/?login=success`와 `AGENTFIT_SESSION` cookie를, 실패·취소면 `/?login=failed`와 세션 cookie 미발급으로 `302` 이동한다. provider token과 오류 원문·`code`·`state`는 URL·응답 본문에 노출하지 않는다.
- `GET /api/auth/csrf`은 `204`와 `XSRF-TOKEN` cookie를 반환한다.
- `POST /api/auth/logout`은 `X-XSRF-TOKEN` 검증에 성공하면 `204`를 반환하고 Spring Session JDBC 서버 세션·`AGENTFIT_SESSION` cookie를 폐기한다. 미로그인은 `401`, CSRF 실패는 `403 CSRF_INVALID`다.

- `GET /api/auth/get-session`은 로그인된 경우 `user.id`, `user.name`, `session.expiresAt`만 포함한 PublicSession을 `200`으로 반환한다.
- 미로그인은 오류가 아니라 `200 null`이다.
- Token, Account, 원본 Spring Session 레코드와 provider token은 응답에 포함하지 않는다.

### Project 공개 DTO

Project는 `id`, `name`, `version`, `createdAt`, `updatedAt`만 공개한다. `ownerId`와 `analysisGeneration`은 공개하지 않는다.
ID는 불투명 문자열이고, 날짜는 UTC ISO 8601, 외곽 필드명은 camelCase다.

### 프로젝트 endpoint

| Method | Path | 성공 응답 | 입력·규칙 |
| --- | --- | --- | --- |
| `POST` | `/api/projects` | `201 { "project": Project }` | JSON `{ "name": string }`; trim 후 1–100 code points, 추가 필드 거부 |
| `GET` | `/api/projects` | `200 { "projects": Project[] }` | query 없음; `updatedAt DESC`, 동률이면 `id ASC`; 페이지네이션 없음 |
| `GET` | `/api/projects/{projectId}` | `200 ProjectDetailResponse` | `project`, `confirmed`, `draft`, `latestAttempt` 키는 항상 존재하며 없는 값은 `null` |
| `DELETE` | `/api/projects/{projectId}` | `204` | 본문 `{ "confirmation": true, "expectedVersion": number }`; confirmation 누락·false는 422, 오래된 expectedVersion은 삭제하지 않고 409 |

상세 조회의 `latestAttempt`가 실패해도 이전 DRAFT·CONFIRMED는 삭제하지 않는다. 204 응답에는 JSON body가 없다.

### 오류 계약

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 잘못된 JSON·헤더 인코딩·요청 문법 |
| 401 | `UNAUTHENTICATED` | 로그인 필요·세션 만료 |
| 403 | `CSRF_INVALID` | 상태 변경 요청의 CSRF token 누락·불일치 |
| 404 | `PROJECT_NOT_FOUND` | 없는 프로젝트·타인 프로젝트를 동일 처리 |
| 422 | `INVALID_INPUT` | 필수/추가 필드·값 범위·confirmation 검증 실패 |
| 409 | `VERSION_CONFLICT` | 삭제를 포함한 보호 쓰기의 Project 또는 선택한 draft 버전 불일치; 프로젝트 자체 수정 endpoint에는 사용하지 않음 |

오류 본문은 `{ "error": { "code", "message", "requestId", "fields"? } }` 구조를 사용하며 원문·Secret·provider 원본 오류·stack trace를 포함하지 않는다.

## 6. 버전과 삭제 Fixture

| Fixture 이름 | 준비 | 행위 | 기대 결과 |
| --- | --- | --- | --- |
| owner-project-read | 소유자와 프로젝트 존재 | 소유자가 프로젝트 조회 | 공개 필드만 반환, ownerId 없음 |
| anonymous-project-read | 인증 주체 없음 | 보호 프로젝트 조회 | 401, 프로젝트 데이터 없음 |
| foreign-project-read | 사용자 A 소유 프로젝트, 사용자 B 인증 | B가 A 프로젝트 조회 | 404 PROJECT_NOT_FOUND, 데이터 없음 |
| unknown-project-read | 인증된 사용자, 없는 프로젝트 | 프로젝트 조회 | foreign-project-read와 같은 404 의미와 데이터 없음 |
| cross-project-child | 서로 다른 프로젝트의 하위 객체 | 프로젝트 A 경로로 B의 하위 객체 요청 | 거부, 어느 프로젝트의 하위 데이터도 변경 없음 |
| forged-owner-input | 인증된 사용자 | ownerId 또는 서버 상태를 포함한 변경 요청 | 입력 거부, 소유권·상태 변경 없음 |
| oauth-callback-session | GitHub OAuth authorize 요청 | 승인된 callback URL로 code·state 반환 | Spring Boot만 token을 교환하고 `github_user_id`로 USER를 조회·생성한 뒤 Spring Session JDBC 서버 세션과 `AGENTFIT_SESSION` 지속 cookie를 만들고 `/?login=success`로 이동, provider token은 저장하지 않음 |
| oauth-callback-redaction | OAuth callback 성공·실패·취소 | callback Location과 Set-Cookie 검사 | 성공은 정확히 `/?login=success`와 `AGENTFIT_SESSION`, 실패·취소는 정확히 `/?login=failed`; `error`·`code`·`state`·token이 Location에 없고 실패·취소에는 세션 cookie 없음 |
| csrf-protected-write | 로그인·CSRF token 존재 | token 누락·불일치와 일치한 각각의 상태 변경 요청 | 앞의 두 요청은 403 `CSRF_INVALID`, 일치한 요청만 다음 검증 단계로 진행 |
| provider-token-not-persisted | GitHub OAuth 로그인 성공 | 사용자 식별 뒤 DB·Spring Session JDBC 테이블·Audit·일반 로그 확인 | GitHub provider access/refresh token 없음 |
| logout-during-analysis | 인증·소유권 검증 및 A6 `PROCESSING` 저장 뒤 사용자 로그아웃 | AI 결과가 Core API에 반환 | 세션 재확인 없이 프로젝트·A6·세대·삭제 상태·deadline 재검증을 통과한 결과만 저장, 재로그인 뒤 조회 가능 |
| stale-project-write | 같은 프로젝트에 기준 버전이 다른 두 변경 | 첫 변경 후 두 번째 변경 | 두 번째 변경은 저장되지 않음, 충돌 안내, 최신 정보 재조회 가능 |
| unconfirmed-delete | 소유 프로젝트와 현재 version | 명시 확인 없는 삭제 요청 | 삭제 없음 |
| stale-delete | 소유 프로젝트와 오래된 expectedVersion | 명시 확인 후 삭제 요청 | 409 VERSION_CONFLICT, 프로젝트·종속 데이터 변경 없음 |
| confirmed-owner-delete | 소유 프로젝트와 현재 version·종속 A·B 데이터 | 명시 확인과 현재 expectedVersion으로 삭제 요청 | 프로젝트·종속 데이터·프로젝트 Audit이 함께 제거되고 공용 Catalog·USER·Spring Session JDBC 데이터·사용자 PC 파일은 유지 |
| running-work-delete | 삭제 중 분석·Review·추천 작업 | 작업 취소 없이 삭제 진행 | 작업 취소 API 없이 삭제 상태·세대·존재 검사를 적용 |
| late-result-after-delete | 삭제된 프로젝트의 이전 분석·Review·추천 결과 | 결과 저장 시도 | 프로젝트·하위 리소스 복원 또는 변경 없음 |

## 7. OpenAPI 동기화 조건

인증·프로젝트의 1차 URL과 기본 DTO는 `contracts/openapi/core-api.yml`에, AI 내부 호출은 `contracts/openapi/ai-service.yml`에 반영한다. 구현 전에는 다음 fixture와 서버 검증을 두 OpenAPI 계약에 맞춘다.

1. OAuth 성공·취소·실패, 세션·CSRF cookie와 logout의 실제 302/204/401/403 fixture
2. 401, 동일 404, 입력 거부, 삭제 확인 실패·오래된 version 삭제와 이후 보호 쓰기의 409 오류 본문 fixture
3. Spring Boot–FastAPI HMAC canonical 문자열·header·timestamp·keyId·중복 요청·A6 context fixture
4. 모든 fixture의 요청·응답 예시와 Core API·AI Service 테스트

