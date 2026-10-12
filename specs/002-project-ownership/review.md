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

## 제한 및 후속 운영 확인

- PostgreSQL 경쟁·삭제 그래프 통합 테스트는 전용 PostgreSQL 환경이 있을 때 실행한다. 로컬 H2·단위 테스트는 동일한 잠금·소유권·지연 결과 경계를 검증한다.
- 실제 GitHub OAuth callback, HTTPS cookie 속성, 정적 Frontend `/api` proxy의 same-origin 여부는 배포 환경 smoke test에서 확인한다.
- 실제 LLM 업무 결과 schema는 `CONTRACT_TEST_DOUBLE` 이후 별도 계약 버전으로 추가한다.
