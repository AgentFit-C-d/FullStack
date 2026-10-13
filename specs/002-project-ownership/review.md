# 독립 코드 리뷰 기록

## 범위

`fullstackA` 브랜치의 Core API 인증·세션, 프로젝트 소유권, 버전 잠금, 삭제 경계,
Audit, A6 분석 실행·결과 저장 검증과 FastAPI HMAC 내부 계약을 다시 검토했다.

## 점검 결과

- 인증 근거는 Spring Session JDBC의 `PRINCIPAL_NAME`이며 브라우저 JWT·Bearer·GitHub token을 프로젝트 API에 사용하지 않는다.
- 프로젝트 조회는 인증 주체와 소유자 일치 조건을 SQL 조회에 포함해 타인·없는 프로젝트를 같은 404로 숨긴다.
- 보호 쓰기는 기준 version을 먼저 확인하고, stale 요청은 callback·version 증가·Audit을 실행하지 않는다.
- 삭제는 확인값·소유권·현재 version을 모두 통과해야 하며, 프로젝트 FK cascade와 `deleteProjectGraph` 경계를 사용한다.
- 분석 결과 저장은 request context·project generation·deadline·삭제 상태를 재검증하며, 로그아웃·브라우저 종료가 서버 소유 실행을 취소하지 않는다.
- HMAC secret, 서명, raw body와 provider token은 Audit·업무 DB·응답에 기록하지 않는다.
- OpenAPI의 camelCase request context와 FastAPI canonical 문자열 순서를 Java signer/Python verifier 테스트가 공유한다.

## 2026-10-13 수렴 결과

- `SecurityConfig.java`의 `AGENTFIT_SESSION`은 host-only·`Path=/`·HttpOnly·SameSite=Lax·7일 Max-Age로 발급된다. `/` 경로를 사용해야 OAuth 성공 redirect 이후 정적 Frontend 요청과 `/api` 요청이 같은 서버 세션을 함께 보낼 수 있다.
- Spring Security는 `maximumSessions(1)`과 Spring Session-backed `SessionRegistry`를 사용한다. 새 로그인은 이전 서버 세션을 만료시키므로 이전 브라우저에 남아 있는 cookie만으로 인증이 유지되지 않는다. `OAuthLoginSecurityTest`가 registry wiring을 검증한다.
- 사용자의 수동 smoke test에서 GitHub OAuth 후 `GET /api/auth/get-session`이 `user.id`, `user.name`, `session.expiresAt`를 반환하고, logout 후 세션이 사라지는 흐름을 확인했다. 정적 Frontend가 없는 현재 개발 실행에서는 성공 redirect의 `/`가 root fallback/500이 될 수 있으며 이는 OAuth 인증 실패가 아니다.
- 프로젝트 CRUD는 `ProjectService` 하나가 create/list/get/delete를 담당하도록 통합했다. `ProjectQueryService`와 `ProjectDeletionService`는 중복 책임이어서 제거했고, 소유권 검사는 `ProjectOwnershipGuard`로 유지했다.

## 제한 및 후속 운영 확인

- PostgreSQL 경쟁·삭제 그래프 통합 테스트는 전용 PostgreSQL 환경이 있을 때 실행한다. 로컬 H2·단위 테스트는 동일한 잠금·소유권·지연 결과 경계를 검증한다.
- 실제 GitHub OAuth callback, HTTPS cookie 속성, 정적 Frontend `/api` proxy의 same-origin 여부는 배포 환경 smoke test에서 확인한다.
- 실제 LLM 업무 결과 schema는 `CONTRACT_TEST_DOUBLE` 이후 별도 계약 버전으로 추가한다.
- 2026-10-13 최종 재실행: JDK 21과 별도 로컬 Maven 저장소를 사용해 `services/core-api`에서 `mvn test`를 실행했고 `52 tests, 0 failures, 0 errors`, `BUILD SUCCESS`를 확인했다. `services/ai-service`는 Python 3.13 가상환경에 개발 의존성을 설치한 뒤 `pytest -q`를 실행해 `10 passed`를 확인했다. 기존 Maven lock 오류는 별도 저장소로 우회했으며 저장소 설정 파일과 임시 Maven 저장소는 작업 후 제거했다. PostgreSQL 전용 경쟁·삭제 그래프 및 배포 smoke는 별도 환경이 없어 NOT_RUN이다.
- 배포 smoke 전제: 정적 Frontend 또는 `/` route가 제공되어 `/?login=success`를 렌더링해야 한다. Core API만 단독 실행하면 해당 redirect가 root fallback/500이 될 수 있으므로, 이 상태는 인증 세션 발급 여부와 분리해 확인한다.
