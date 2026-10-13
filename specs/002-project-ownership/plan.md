# Implementation Plan: 프로젝트 소유권과 접근 격리

**Branch**: [002-project-ownership] | **Date**: 2026-10-06 | **Spec**: [spec.md](./spec.md)

**Input**: 개인 소유 프로젝트의 인증 주체 연결, 소유권 검사, 정보 은닉, 버전 충돌, 삭제와 Audit 요구사항

## Summary

이 기능은 Core API가 인증된 주체와 프로젝트를 안전하게 연결하고, 프로젝트와 모든 하위 리소스에 같은 소유권 경계를 강제하게 만든다. 비로그인·타인·없는 프로젝트 처리, 입력 위조 차단, 버전 충돌, 삭제와 지연 결과 차단을 함께 검증한다.

이번 계획은 2026-10-07에 승인된 프로젝트·세션 API 계약을 기준으로 논리 데이터 모델, 구현 순서와 검증 경계를 정한다. Core API의 소유권·세션 경계, 삭제·버전·Audit, AI 내부 HMAC 계약과 마이그레이션은 구현되었고, 실제 OAuth/HTTPS·PostgreSQL 운영 환경 검증은 별도 smoke 단계로 남겨 둔다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.5 Web·Validation·Test와 Spring Security 기반 세션 경계를 사용한다. JDBC/Flyway 마이그레이션과 AI 내부 HMAC 계약 클라이언트·검증기를 포함하며, 실제 OAuth provider·PostgreSQL 운영 연결은 배포 환경에서 주입한다.

**Storage**: PostgreSQL을 Core API만 읽고 쓴다. 프로젝트 version/generation과 A6 분석 시도, Audit을 저장하며 삭제 그래프와 경쟁 갱신은 트랜잭션·행 잠금으로 경계를 둔다.

**Testing**: JUnit 5·MockMvc 기반 Core API 테스트와 FastAPI pytest 계약·HMAC 테스트를 유지한다. 테스트 소스와 수동 OAuth smoke 경로는 구현됐지만, 현재 검증 실행은 Maven 로컬 repository lock 때문에 Core API 결과가 NOT_RUN이며 PostgreSQL 경쟁·삭제 그래프와 실제 OAuth/HTTPS 배포 검증은 전용 환경에서 별도 확인한다.

**Target Platform**: 정적 Frontend와 Core API를 같은 Origin으로 제공하고 `/api`로 프록시하는 웹 서비스 경계다. OAuth 시작·callback도 `/api` 아래에서 처리하며, Frontend는 UI·CSRF header·재로그인 안내를 담당한다. AI Service는 이 기능의 권한 판정·DB 접근에 참여하지 않는다.

**Project Type**: 단일 Spring Boot Core API 안의 A 도메인 기능과 Frontend-Core 계약 기능

**Performance Goals**: 수치형 처리량 목표는 문서에 없다. 완료 기준은 보호 요청에서 무단 조회·변경 0건, 삭제 후 지연 결과 복원 0건, 지정된 충돌 시 조용한 덮어쓰기 0건이다.

**Constraints**: 개인 소유만 지원, 서버 인증 주체로만 소유자 결정, 타인·없는 프로젝트 404 은닉, 공개 결과의 ownerId 금지, 상태 변경과 Audit의 원자성, 프로젝트 우선 잠금과 기준 버전 확인을 지킨다.

**Scale/Scope**: MVP의 개인 소유 프로젝트 경계만 다룬다. 팀 공유·초대·소유권 이전, 사용자 계정 삭제, 로그인 UI, 실제 LLM 모델 규칙과 문서 의미 해석은 범위 밖이다. 다만 A6 분석 시도 기록, 브라우저 연결·사용자 세션과 독립된 Core API 소유 실행 경계, `POST /internal/v1/analysis` HMAC 호출·검증 계약, 결과 저장 전 소유권·세대·삭제 검증은 이 기능의 안전한 저장 경계로 포함한다.

## Global Constraints

- 프로젝트 소유자는 서버가 확인한 인증 주체로만 정하며, 클라이언트 입력의 ownerId는 신뢰하지 않는다.
- 모든 보호 작업은 인증·소유권·프로젝트 경계·허용 입력을 서버에서 다시 검사한다.
- 비로그인은 401, 타인과 없는 프로젝트는 같은 404 PROJECT_NOT_FOUND로 정보 없이 처리한다.
- 공개 결과에는 소유자 식별자를 넣지 않고, Audit에는 원문·Secret·원시 AI 응답을 넣지 않는다. Audit의 내부 행위자 참조는 최소 추적 정보로만 유지한다.
- 충돌한 쓰기는 저장하지 않으며 자동 병합·치환·조용한 덮어쓰기를 하지 않는다.
- 성공 상태 변경·버전·Audit, 그리고 삭제 그래프는 각각 같은 트랜잭션 경계에서 검증한다.

## Review Focus

- 인증이 없거나 처리 중 만료된 요청이 프로젝트·하위 리소스 정보를 전혀 받지 못하는지 확인한다.
- 타인 프로젝트와 존재하지 않는 프로젝트가 상태·본문·데이터 유무로 구분되지 않는지 확인한다.
- 다른 프로젝트의 하위 객체 식별자, ownerId, 서버 상태, Audit 주입이 소유권이나 상태를 바꾸지 못하는지 확인한다.
- 오래된 기준 버전의 두 번째 변경이 첫 변경을 보존하고 최신 정보 재조회를 안내하는지 확인한다.
- 삭제와 늦은 분석·Review·추천 결과가 경쟁해도 프로젝트·종속 데이터가 다시 생기지 않는지 확인한다.

## Constitution Check

*GATE: Phase 0 조사 전과 Phase 1 설계 후에 확인한다.*

| 헌장 원칙 | 설계 판정 | 계획에서의 준수 방법 |
| --- | --- | --- |
| I. 책임 경계와 단일 쓰기 소유권 | 통과 | 브라우저는 Core API만 호출하고, PostgreSQL 쓰기·권한·Audit은 Core API가 맡는다. Frontend·AI Service의 DB 직접 접근이나 권한 판정은 계획하지 않는다. |
| II. AI 제안과 사용자 확정 분리 | 통과 | 이 기능은 AI 결과를 확정하지 않는다. 나중에 도착한 AI 결과는 Core API가 프로젝트 존재·버전·세대를 다시 확인한 뒤에만 반영한다. |
| III. 소유권 검증과 안전한 접근 | 통과 | 모든 보호 작업에서 서버 인증 주체, 프로젝트 소유권, 하위 리소스 귀속과 허용 입력만 검사한다. |
| IV. 버전·경쟁·실패 보존 | 통과 | 기준 버전과 프로젝트 우선 잠금을 검증하고, 충돌 시 저장하지 않은 채 최신 정보 재조회를 안내한다. |
| V. 상태와 Audit의 원자성 | 통과 | 성공 변경·버전·Audit 및 삭제 그래프를 각각 하나의 Core API 트랜잭션 경계로 설계한다. 지연 결과는 저장 직전에 재검증한다. |
| VI. 계약 우선 | 통과 | 인증·프로젝트 URL·DTO·오류·CSRF 계약은 Core OpenAPI에, HMAC AI 호출 계약은 AI OpenAPI에 반영했다. 이후 구현은 두 계약과 fixture를 서버·테스트에 같은 변경으로 맞춘다. |
| VII. 작은 변경·실패 테스트·독립 리뷰 | 통과 | 요구사항별 실패 테스트를 먼저 만들고, 기존 얇은 컨트롤러·record DTO·MockMvc 관례를 따른다. 구현 후 별도 리뷰에서 소유권·동시성·삭제·계약을 확인한다. |

**구현 재검토 결과**: 인증·프로젝트·AI 호출 API 계약, USER 확장·Spring Session JDBC·PROJECT·AUDIT·ANALYSIS_ATTEMPT migration, Spring Boot 인증 경계, 삭제·버전·Audit·Core API AI client·FastAPI HMAC contract test-double이 구현됐다. PostgreSQL 경쟁·실제 OAuth/HTTPS 배포 smoke test와 실제 LLM 업무 규칙은 전용 환경 또는 별도 기능에서 검증한다. 헌장 위반 없음.

## Research Decisions

조사 근거와 대안은 [research.md](./research.md)에 정리한다.

1. Core API의 프로젝트 소유권 경계가 유일한 업무 쓰기 주체다.
2. Spring Boot가 `/api/oauth2/authorization/github`와 `/api/login/oauth2/code/github`에서 GitHub OAuth·PostgreSQL 기반 Spring Session JDBC 세션 발급을 직접 담당한다. OAuth callback은 `github_user_id BIGINT`로만 기존 USER를 찾고 없으면 `github_login VARCHAR(255)`을 함께 채운 새 USER를 만들며, 이메일·이름·login 자동 연결은 금지한다. 기존 행은 null GitHub ID를 보존하고, null이 아닌 ID만 유일하다. 세션은 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` 기본 테이블과 `PRINCIPAL_NAME` 내부 userId를 사용한다. 같은 Origin에서 이름이 `AGENTFIT_SESSION`인 host-only, `Path=/`, 운영 환경 `Secure`, `SameSite=Lax`, `Max-Age=7일` 지속 HttpOnly 쿠키로 인증 주체를 소비한다. `Path=/`는 로그인 성공 redirect인 `/`와 `/api` 요청 모두에 세션을 전달한다. Spring Security `maximumSessions(1)`로 사용자당 활성 서버 세션을 하나로 제한하며 새 로그인이 이전 세션을 만료시킨다. 서버 세션은 마지막 인증된 요청 후 7일 비활성 시 만료하고, 쿠키는 자동 연장하지 않으며 로그아웃은 즉시 폐기한다. CORS credential 설정은 사용하지 않고, `GET /api/auth/csrf`가 발급한 `Path=/`·비-HttpOnly `XSRF-TOKEN`과 상태 변경 요청의 `X-XSRF-TOKEN`으로 CSRF를 검증한다.
3. 프로젝트·세션의 1차 URL, 기본 DTO, 401/403/404/422 오류 의미, 이름 길이, 삭제 확인 형식은 승인했다. 프로젝트 자체 수정 endpoint와 해당 409는 범위에서 제외한다. `POST /internal/v1/analysis`의 HMAC header·canonical 문자열·timestamp·오류 의미도 승인했으며, Profile·DRAFT·Attempt의 상세 업무 schema와 실제 LLM 규칙은 후속 기능으로 남긴다.
4. 데이터 모델은 논리 관계를 중심으로 기록하되, USER GitHub 식별자 필드·partial unique 규칙, Spring Session JDBC 기본 schema·cookie 이름, Flyway migration 경로는 승인된 인증 계약으로 포함한다. 전체 ERD·DDL과 프로젝트·하위 업무 데이터의 물리 구조는 후속 단계에서 정한다.

## Project Structure

### Documentation (this feature)

    specs/002-project-ownership/
    ├── spec.md                         # 기능 요구사항과 clarify 결과
    ├── plan.md                         # 이 구현 계획
    ├── research.md                     # 근거와 설계 결정
    ├── data-model.md                   # 논리 모델, ERD/DDL 아님
    ├── quickstart.md                   # 구현 후 검증 안내
    ├── contracts/
    │   ├── README.md                   # 계약 문서의 역할과 OpenAPI 승격 조건
    │   └── project-ownership.md        # URL 비종속 소유권/오류/fixture 계약
    └── tasks.md                        # speckit-tasks 단계에서만 생성

### Source Code (planned target structure)

    services/core-api/
    ├── pom.xml                         # 승인된 최소 의존성만 추가
    └── src/
        ├── main/
        │   ├── java/com/agentfit/coreapi/
        │   │   ├── CoreApiApplication.java
         │   │   ├── auth/               # GitHub OAuth·서버 세션·인증 주체 경계
        │   │   ├── project/            # API, application, domain, persistence의 기능 국소화
        │   │   ├── analysis/           # A6 실행·AI client·결과 저장 보호 경계
        │   │   ├── audit/              # 최소 Audit 기록
        │   │   └── error/              # 401/404/검증/충돌의 공통 표현
        │   └── resources/
        │       └── application.yml     # Secret 없는 환경 설정만
        └── test/
            └── java/com/agentfit/coreapi/
                ├── project/            # 소유자·입력·버전·삭제 테스트
                ├── audit/              # 원자성·민감정보 제외 테스트
                └── contract/           # 정상·401·동일 404·충돌 fixture 테스트

    contracts/openapi/core-api.yml      # 인증·프로젝트 공유 계약의 실제 기준 파일
    contracts/openapi/ai-service.yml    # HMAC 기반 내부 AI 호출의 실제 기준 파일
    apps/web/                            # 로그인/오류 화면은 Frontend 범위이며 이 계획에서 수정하지 않음
    services/ai-service/                 # HMAC verifier만 제공, 권한·DB를 소유하지 않음

**Structure Decision**: 현재 Core API와 AI Service 코드는 health endpoint만 가진 평면 스캐폴드다. 기능 구현 시에만 Core API를 auth, project, analysis, audit, error로 국소화하고, AI Service에는 계약된 HMAC verifier와 `/internal/v1/analysis`만 추가한다. AI 모델의 세부 업무는 이 기능에서 구현하지 않는다.

## Delivery Workstreams

### 1. 계약 결정 게이트

인증·프로젝트·AI 내부 호출의 1차 계약은 두 공유 OpenAPI에 반영됐다. OAuth 완료 이동은 성공 `/?login=success`, 실패·취소 `/?login=failed`로 확정했다. 인증 구조는 기존 USER의 `github_user_id BIGINT NULL`·`github_login VARCHAR(255) NULL`, null이 아닌 GitHub ID의 유일성, 자동 계정 연결 금지, Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`, `AGENTFIT_SESSION` cookie, `services/core-api/src/main/resources/db/migration/`의 Flyway versioned SQL로 확정했다. 구현 전에 남은 계약 게이트는 Profile·DRAFT·Attempt의 상세 업무 schema와 실제 LLM 규칙이다.

### 2. Core API 기반과 인증 주체 경계

실패 테스트로 OAuth 시작·callback 경로, GitHub ID 기반 USER 생성·조회와 자동 연결 금지, CSRF token 누락·불일치 403, `AGENTFIT_SESSION` 쿠키 없음·7일 비활성 세션 만료·로그아웃 폐기·서버 주체 기반 소유자 결정을 먼저 표현한다. Spring Boot는 OAuth callback·token 교환·PostgreSQL Spring Session JDBC 세션 발급을 담당하고, Core API는 `AGENTFIT_SESSION` 쿠키로 `SPRING_SESSION`의 `PRINCIPAL_NAME` 내부 userId를 소비한다. 이 인증 경계가 제공되기 전에는 고정 테스트 세션을 사용한 도메인 검증까지만 완료로 본다.

### 3. 프로젝트와 접근 격리

프로젝트 생성·목록·상세의 서버 소유자 결정을 구현하고, 모든 보호 작업의 공통 소유권 검사와 하위 리소스 귀속 검사를 적용한다. 비로그인은 401, 타인과 없는 프로젝트는 데이터 없이 같은 404 PROJECT_NOT_FOUND가 되는 fixture를 먼저 실패시킨다. 공개 결과와 입력에서 ownerId·서버 상태·Audit 주입을 차단한다.

### 4. 버전·Audit·삭제

기준 버전 확인과 프로젝트 우선 잠금 규약을 적용한다. 프로젝트 자체 수정 endpoint는 제공하지 않으며, 이후 Profile·분석·하위 업무 쓰기가 재사용할 `ProjectWriteGuard` 경계에서 오래된 쓰기를 저장하지 않고 최신 상태 재조회를 안내한다. 성공 상태 변경·버전·최소 Audit을 하나의 트랜잭션으로 저장하며, 삭제도 소유자·명시 확인·현재 `expectedVersion`을 확인하고 PROJECT 잠금 뒤에만 수행한다. 오래된 삭제는 409로 거부하고, 일치한 삭제만 역참조 처리 후 프로젝트 종속 A·B 업무 데이터를 같은 작업으로 제거한다. 공용 Catalog·계정·Session·사용자 PC 파일은 보존한다.

### 5. 지연 결과와 통합 검증

Core API는 A6를 커밋한 뒤 브라우저 HTTP 연결·사용자 세션과 독립된 작업으로 `/internal/v1/analysis`를 호출하고, AI Service는 HMAC·timestamp·중복 요청만 검증한다. HTTP 본문의 `requestId`·`generation`·`contractVersion`은 A6의 `request_id`·`generation`·`contract_version`을 각각 매핑한 camelCase wire 필드다. canonical 문자열은 `METHOD`, `PATH`, `X-AgentFit-Timestamp`, 세 wire 문맥, UTF-8 raw body의 소문자 SHA-256 hex를 줄바꿈 순서로 연결하고 서명은 padding 없는 base64url HMAC-SHA256으로 전송한다. 실제 모델 규칙은 별도 기능이므로 이 기능의 AI Service는 유효 요청에 `result.kind = CONTRACT_TEST_DOUBLE`만 반환하고, Core API는 이를 DRAFT·추천 업무 데이터로 변환하지 않은 채 A6·프로젝트 결과 저장 경계만 검증한다. 연결 전 실패와 AI Service가 아직 처리하지 않았음을 보장한 503만 같은 `requestId`·`generation`으로 최대 1회 재시도하고, 전송 후 timeout 또는 처리 여부가 불명확한 5xx는 자동 재시도하지 않은 채 A6 deadline을 따른다. 결과 저장 직전에는 프로젝트 존재·소유권·상태·기준 버전·세대를 재검증한다. 실행 중 작업을 강제 취소하지 않아도 삭제와 지연 결과를 의도적으로 경쟁시키고, 로그아웃·세션 만료·브라우저 연결 종료 뒤 도착한 유효 결과는 저장하되 데이터를 되살리지 않는 통합 검증을 추가한다. 구현 완료 전 Core·AI 계약 fixture, 웹 테스트, PostgreSQL 트랜잭션 테스트, 독립 리뷰를 모두 통과시킨다.

## Implementation Coverage

| Spec requirement | 계획 위치 |
| --- | --- |
| FR-OWN-001~004 개인 소유·GitHub 식별·서버 소유자·기본 작업 | Workstreams 1~3, data-model.md, contracts/project-ownership.md |
| FR-OWN-005~008 전 보호 작업·404 은닉·하위 격리·입력 경계 | Workstream 3, 계약 fixture, quickstart 접근 격리 시나리오 |
| FR-OWN-009 버전 충돌 | Workstream 4, 논리 모델의 version, quickstart 경쟁 시나리오 |
| FR-OWN-010~012 삭제·지연 결과·Audit | Workstreams 4~5, A6 분석 시도·HMAC 호출 경계·삭제 그래프와 지연 결과 재검증 규칙 |
| FR-OWN-013 계약 정합성 | Workstreams 1·5, 계약 문서와 실제 OpenAPI 승격 조건 |

## Deferred Decisions and Delivery Gates

| 항목 | 현재 결정 | 구현 시작 전 필요한 조치 |
| --- | --- | --- |
| GitHub OAuth·세션·USER 확장 구조 | 2026-10-08 사용자 승인: 같은 Origin의 `/api`, OAuth 시작 `/api/oauth2/authorization/github`, callback `/api/login/oauth2/code/github`, 성공 `/?login=success`, 실패·취소 `/?login=failed`, 기존 `USER`에 `github_user_id BIGINT NULL`·`github_login VARCHAR(255) NULL` 추가, null이 아닌 GitHub ID만 유일, GitHub ID로만 조회하고 이메일·이름·login 자동 연결 금지, 불일치 시 새 USER 생성, `OAUTH_ACCOUNT`·별도 AgentFit `SESSION` 테이블 없음, GitHub 단일 provider와 `user:email`, Spring Session JDBC 기본 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`와 `PRINCIPAL_NAME` 내부 userId, 이름이 `AGENTFIT_SESSION`인 host-only·`Path=/api`·운영 환경 `Secure`·`SameSite=Lax`·`Max-Age=7일` 지속 HttpOnly 쿠키, 마지막 인증된 요청 후 7일 비활성 만료, 쿠키 자동 연장 없음, CSRF token endpoint·header, 로그아웃 즉시 폐기, CORS credential·provider token·JWT/Bearer 미사용, Flyway versioned SQL은 `services/core-api/src/main/resources/db/migration/`에 저장 | Flyway의 다음 버전 번호를 사용해 USER 확장·partial unique index·Spring Session JDBC schema를 적용하고, 스테이징에서 기존 null GitHub ID USER·신규 USER 생성·static Frontend `/api` proxy same-origin·HTTPS cookie·OAuth callback을 검증 |
| Spring Boot–FastAPI HMAC 세부 계약 | `POST /internal/v1/analysis`, camelCase wire 문맥 `requestId`·`generation`·`contractVersion`, HMAC-SHA256, exact canonical 문자열, padding 없는 base64url signature, timestamp ±5분, 현재·이전 key 교체, FastAPI DB 미접근, 10분 중복 억제, 제한형 재시도, `401 INTERNAL_AUTH_FAILED`, contract-only 응답 `CONTRACT_TEST_DOUBLE`을 확정 | 배포 시 secret manager에 `AGENTFIT_HMAC_CURRENT_KEY_ID`·`AGENTFIT_HMAC_CURRENT_SECRET`을 함께 주입한다. 이전 키를 쓰면 `AGENTFIT_HMAC_PREVIOUS_KEY_ID`·`AGENTFIT_HMAC_PREVIOUS_SECRET`·`AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL` 세 값을 함께 주입하고 만료 뒤 거부한다. 현재 키 쌍이 없거나 이전 키 값이 일부만 있으면 두 서비스 모두 시작에 실패한다. 실제 LLM 입력·결과 업무 schema는 별도 AI 기능에서 합의 |
| 프로젝트·세션 API 계약 | `/api/projects`, `/api/projects/{projectId}`, `/api/auth/get-session`, `/api/auth/csrf`, `/api/auth/logout`, Project 공개 DTO, 이름 trim 후 1–100 code points, 목록 정렬, `{ "confirmation": true, "expectedVersion": number }`, 삭제 stale version을 포함한 401/403/404/409/422 오류 의미와 공개 오류 envelope를 확정 | Profile·DRAFT·Attempt의 세부 schema와 실제 정상·실패 fixture를 추가하고, Core API 구현·테스트와 같은 변경으로 검증 |
| ERD·DDL·인덱스·격리 수준 | 인증 USER 확장·partial unique index와 Spring Session JDBC schema는 Flyway versioned SQL로 확정 | 프로젝트·하위 업무 데이터의 ERD·DDL·인덱스·격리 수준을 데이터 모델/운영 요구를 바탕으로 별도 설계 |
| 사용자 삭제 후 백업·복제본의 물리 삭제 증명 | 미확정 | 보관·운영 정책을 별도 결정 |

tasks.md와 기능 구현은 위 게이트 중 해당 작업에 필요한 계약이 합의된 뒤에만 시작한다.

### Convergence note (2026-10-13)

The earlier 2026-10-08 delivery-gate row records the original `Path=/api` proposal. The implemented and current delivery policy is `Path=/` for `AGENTFIT_SESSION`, so the cookie reaches both the `/` login-success redirect and `/api` requests. The current policy also limits each user to one active server session with `maximumSessions(1)`; see `spec.md` Convergence and `quickstart.md` for the verification procedure.

