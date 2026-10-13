# 검증 안내: 프로젝트 소유권과 접근 격리

**Status**: 구현·테스트 절차와 최종 로컬 검증 결과가 반영된 안내다. Core API와 AI Service 자동화 테스트는 통과했으며, PostgreSQL 전용 경쟁·삭제 그래프와 실제 GitHub/HTTPS 배포 smoke test는 전용 환경에서 실행한다.

## 1. 사전 조건

- JDK 21과 Maven을 준비한다. 현재 작업 환경에서는 mvn 실행 파일이 PATH에 없었으므로 설치 또는 경로 설정을 먼저 확인한다.
- 실제 인증 경계가 준비되어 있어야 한다. 두 개의 서로 다른 GitHub 인증 주체와 GitHub OAuth App callback URL `https://{origin}/api/login/oauth2/code/github`를 준비한다.
- 인증 테스트는 같은 Origin에서 Spring Boot가 발급한 이름이 `AGENTFIT_SESSION`인 host-only·`Path=/`·운영 환경 `Secure`·`SameSite=Lax`·`Max-Age=7일` 지속 HttpOnly 세션 쿠키, 없는 쿠키, 쿠키 발급 뒤 7일이 지나 만료된 쿠키, PostgreSQL Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`에서 마지막 인증 요청 후 7일이 지나 만료되거나 로그아웃으로 폐기된 서버 세션을 각각 준비한다. `Path=/`는 `/` 로그인 성공 redirect와 `/api` 요청 모두에 세션을 전달한다. 사용자별 활성 서버 세션은 하나이며 새 로그인 시 기존 세션이 만료된다. 먼저 `GET /api/auth/csrf`로 `XSRF-TOKEN`을 받고 모든 상태 변경 요청에는 같은 값을 `X-XSRF-TOKEN` header로 보낸다. JWT/Bearer 본문과 CORS credential 설정은 준비하지 않는다.
- PostgreSQL 연결과 물리 schema/migration이 승인된 뒤에는 전용 테스트 데이터베이스 또는 동등한 PostgreSQL 테스트 환경을 사용한다.
- 실제 URL·DTO와 오류 형식은 공유 OpenAPI가 승인된 버전이어야 한다.

## 2. 기본 빌드·테스트

구현 후 Core API 디렉터리에서 전체 테스트를 실행한다.

    Set-Location C:\dev_source\agentFit\services\core-api
    mvn test

예상 결과는 health 테스트와 프로젝트 소유권의 단위·웹·계약·트랜잭션 테스트가 모두 통과하는 것이다. Maven이나 PostgreSQL 전제 조건이 충족되지 않으면 통과라고 표시하지 않는다.

현재 검증 기록:

- `services/ai-service`: `pytest`는 프로젝트 dev dependency로 제공되며, 설치된 환경에서 실행한다. HMAC·계약 테스트가 포함된다.
- `services/core-api`: JDK 21과 Maven으로 `mvn test` 실행, Java 단위·MockMvc 테스트 통과. PostgreSQL 전용 경쟁 테스트는 환경 미설정 시 NOT_RUN으로 기록한다.
- 2026-10-12 기존 기록: Core API `mvn test` 통과, AI Service `pytest tests -q` 10개 통과, `compileall` 통과로 기록되어 있다. PostgreSQL 전용 경쟁·삭제 그래프와 실제 OAuth/HTTPS smoke test는 전용 환경이 없어 NOT_RUN이다.
- 2026-10-13 최종 재실행: JDK 21과 별도 로컬 Maven 저장소로 Core API `mvn test`를 실행해 `52 tests, 0 failures, 0 errors`, `BUILD SUCCESS`를 확인했다. AI Service는 Python 3.13 가상환경에서 `pytest -q`를 실행해 `10 passed`를 확인했다. PostgreSQL 전용 경쟁·삭제 그래프와 실제 OAuth/HTTPS 배포 smoke test는 전용 환경이 없어 NOT_RUN이다.
- 정적 검증: `pom.xml` XML 파싱, YAML 파싱, `git diff --check` 통과.

## 3. 필수 검증 순서

1. 정적 Frontend와 `/api` proxy가 실제로 같은 HTTPS Origin인지, GitHub OAuth App callback이 `https://{origin}/api/login/oauth2/code/github`인지 확인한다. `GET /api/oauth2/authorization/github`이 GitHub로 이동하고, `/api/login/oauth2/code/github` callback 뒤 성공하면 정확히 `Location: /?login=success`와 이름이 `AGENTFIT_SESSION`인 cookie가 발급되며, 실패·취소면 정확히 `Location: /?login=failed`와 세션 cookie 미발급인지 확인한다. callback은 `github_user_id`로만 기존 USER를 찾고, 없으면 `github_user_id`·`github_login`을 채운 새 USER를 만들며 이메일·이름·login으로 기존 USER를 자동 연결하지 않아야 한다. provider의 error·token·code·state는 URL에 노출하지 않고, access/refresh token은 DB·Spring Session JDBC 테이블·Audit·일반 로그에 남아서는 안 된다.
1. 정적 Frontend와 `/api` proxy가 실제로 같은 HTTPS Origin인지, GitHub OAuth App callback이 `https://{origin}/api/login/oauth2/code/github`인지 확인한다. `GET /api/oauth2/authorization/github`이 GitHub로 이동하고, `/api/login/oauth2/code/github` callback 뒤 성공하면 정확히 `Location: /?login=success`와 이름이 `AGENTFIT_SESSION`, `Path=/`인 cookie가 발급되며, 실패·취소면 정확히 `Location: /?login=failed`와 세션 cookie 미발급인지 확인한다. callback은 `github_user_id`로만 기존 USER를 찾고, 없으면 `github_user_id`·`github_login`을 채운 새 USER를 만들며 이메일·이름·login으로 기존 USER를 자동 연결하지 않아야 한다. provider의 error·token·code·state는 URL에 노출하지 않고, access/refresh token은 DB·Spring Session JDBC 테이블·Audit·일반 로그에 남아서는 안 된다. 현재 정적 Frontend가 없으면 Core API의 `/` root fallback이 500을 보여도 OAuth callback과 세션 발급 여부는 별도로 확인한다.
2. 유효 세션의 `GET /api/auth/get-session`이 `user.id`·`user.name`·`session.expiresAt`만 반환하고 token·Account·원본 Spring Session 레코드를 반환하지 않는지 확인한다. `AGENTFIT_SESSION` 쿠키 없음·없는/만료/폐기 서버 세션은 오류가 아닌 `200 null`인지 확인한다.
3. `GET /api/auth/csrf`가 `XSRF-TOKEN`을 발급하는지 확인하고, token을 생략하거나 다른 값으로 `POST /api/projects`, `DELETE /api/projects/{projectId}`, `POST /api/auth/logout`을 보내면 모두 403 `CSRF_INVALID`인지 확인한다.
4. 소유자 A가 올바른 CSRF header로 프로젝트를 생성하고 목록·상세에서 자신의 프로젝트만 확인한다.
5. 비로그인 요청이 401이고 프로젝트 정보를 돌려주지 않는지 확인한다.
6. 사용자 B의 타인 프로젝트 요청과 존재하지 않는 프로젝트 요청이 같은 404 PROJECT_NOT_FOUND와 빈 데이터 결과인지 비교한다.
7. 프로젝트 A의 경로에 프로젝트 B의 하위 객체 식별자를 넣어도 읽기·변경이 모두 거부되는지 확인한다.
8. 요청에 ownerId, 서버 상태, Audit 필드를 넣어도 소유권·상태가 바뀌지 않는지 확인한다.
9. 같은 프로젝트에 오래된 기준 버전의 두 번째 변경을 보내 저장되지 않고 최신 정보를 다시 조회할 수 있는지 확인한다.
10. 소유자·명시 확인·현재 `expectedVersion`·유효 CSRF token이 있는 삭제만 성공하고, 오래된 version 삭제는 409이며 어떤 프로젝트 데이터도 제거하지 않는지 확인한다. 성공 시 종속 A·B 데이터와 프로젝트 Audit이 함께 제거되고 공용 Catalog·USER·Spring Session JDBC 데이터·사용자 PC 파일은 남는지 확인한다.
11. 삭제 전 시작된 분석·Review·추천 작업을 강제 취소하지 않아도, 나중에 도착한 결과가 삭제 상태·세대·존재 검사에 의해 저장되지 않고 프로젝트 또는 하위 데이터가 복원되지 않는지 확인한다.
12. 인증·소유권 검증과 A6 `PROCESSING` 저장 뒤 사용자를 로그아웃시키거나 브라우저 연결을 종료한 뒤 AI 결과를 반환한다. 프로젝트·A6·세대·삭제 상태·deadline이 유효하면 결과가 저장되고, 재로그인 뒤 조회할 수 있는지 확인한다.
13. 현재 HMAC key 쌍이 없거나 이전 keyId·secret·유효 시각이 일부만 있으면 두 서비스가 시작에 실패하는지 확인한다. 유효 key로 Core API가 `/internal/v1/analysis`에 보낸 요청은 camelCase `requestId`·`generation`·`contractVersion`, exact canonical 문자열, padding 없는 base64url HMAC을 사용하고 `result.kind = CONTRACT_TEST_DOUBLE` 200을 받는지 확인한다. canonical의 method·path·timestamp·A6 context·UTF-8 raw body hash가 하나라도 맞지 않거나 timestamp가 ±5분을 넘거나 같은 requestId·body hash가 10분 안에 재전송되면 AI Service가 동일한 401 `INTERNAL_AUTH_FAILED`로 거부하는지 확인한다.

Fixture의 의미와 기대 결과는 [contracts/project-ownership.md](./contracts/project-ownership.md)를 기준으로 한다. 논리 데이터 관계는 [data-model.md](./data-model.md)를 기준으로 한다.

## 4. 완료 판정

- 모든 보호 작업 fixture에서 비로그인·비소유 사용자의 타인 데이터 조회·변경 성공이 0건이다.
- 타인 프로젝트와 없는 프로젝트의 정보 은닉 결과가 구분되지 않는다.
- 충돌 요청의 조용한 덮어쓰기와 삭제 뒤 지연 결과 복원이 0건이다.
- 로그아웃·세션 만료·브라우저 연결 종료는 이미 접수된 A6 작업을 취소하지 않으며, 유효한 결과는 재로그인 뒤 조회할 수 있다.
- OAuth callback·CSRF·logout과 AI HMAC 요청의 정상·실패 fixture가 각 OpenAPI·서버 검증·테스트에서 같은 의미를 가진다.
- 성공 상태 변경·버전·Audit 및 삭제 그래프가 요구된 트랜잭션 경계에서 검증된다.
- 공유 OpenAPI, Core API 구현, 테스트 fixture, Frontend가 사용하는 공개 필드가 같은 계약 버전을 가리킨다.

