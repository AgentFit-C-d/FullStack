# 논리 데이터 모델: 프로젝트 소유권과 접근 격리

**Feature**: [프로젝트 소유권과 접근 격리](./spec.md)
**Scope**: 논리 관계를 중심으로 다루되, 승인된 인증 식별자 타입·유일성 제약과 Spring Session JDBC 기본 테이블은 예외로 명시한다. 전체 ERD, SQL DDL, FK, migration 파일과 프로젝트·하위 업무 데이터의 물리 schema는 후속 구현 설계에서 정한다.

## 1. 인증 주체

| 항목 | 규칙 |
| --- | --- |
| 의미 | 보호 작업을 요청한 로그인 사용자다. Core API는 Spring Session JDBC의 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`에 저장된 세션의 `PRINCIPAL_NAME`(내부 userId)을 인증 주체로 사용한다. 브라우저의 인증 근거는 이름이 `AGENTFIT_SESSION`인 `Max-Age=7일` 지속 HttpOnly 세션 쿠키뿐이며, `Path=/`의 읽기 가능한 `XSRF-TOKEN`은 CSRF 검증용이지 인증·소유권 근거가 아니다. |
| 소유권 | 프로젝트를 만든 인증 주체가 유일한 소유자다. 소유권은 클라이언트 입력으로 정하거나 변경할 수 없다. |
| 공개 여부 | 공개 프로젝트 결과에 소유자 식별자를 포함하지 않는다. |
| 계정·세션 수명 | 프로젝트 삭제는 인증 주체의 계정이나 세션을 삭제하지 않는다. |

세션 검증은 `AGENTFIT_SESSION` 쿠키의 세션 토큰으로 PostgreSQL Spring Session JDBC 기본 테이블을 조회하고, 존재·미만료·미폐기·`PRINCIPAL_NAME`의 내부 userId 연결을 확인한다. 세션 자체를 프로젝트 소유자로 저장하거나 클라이언트가 보낸 userId·ownerId를 대체 근거로 사용하지 않는다.

인증 논리 객체는 Spring Boot OAuth2 로그인과 서버 세션 책임에 따라 분리한다.

- **USER**: 서비스 사용자와 프로젝트 소유권의 기준이 되는 기존 사용자 정보다. GitHub 단일 provider를 위해 `github_user_id BIGINT NULL`과 `github_login VARCHAR(255) NULL`을 직접 둔다. null이 아닌 `github_user_id`만 유일하며 `github_login`은 표시·로그용이고 유일 제약이나 사용자 식별에 사용하지 않는다. OAuth callback은 `github_user_id`로만 기존 USER를 찾고, 일치하지 않으면 두 GitHub 필드를 채운 새 USER를 만든다. 이메일·이름·`github_login`으로 기존 USER를 자동 연결하지 않는다.
- **Spring Session JDBC 세션**: Spring Session이 관리하는 `SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES` 기본 테이블이다. `PRINCIPAL_NAME`은 AgentFit 내부 userId이며, Core API는 이 세션을 조회해 인증 주체를 얻는다. 브라우저는 host-only·`Path=/api`·`HttpOnly`·운영 환경 `Secure`·`SameSite=Lax`·`Max-Age=7일`인 `AGENTFIT_SESSION` 쿠키를 보유하며, 마지막 인증 요청 후 7일 비활성·쿠키 만료·로그아웃 중 먼저 발생한 때 세션을 폐기한다. `GET /api/auth/csrf`가 제공하는 `XSRF-TOKEN`은 이 세션에 저장하는 사용자 식별자가 아니며, 상태 변경 요청의 별도 검증 값이다. 별도 AgentFit `SESSION` 테이블은 만들지 않는다.
- **VERIFICATION**: 후속 인증 기능이 필요할 때만 별도 명세로 검토하는 검증용 객체다. 현재 GitHub 로그인 MVP에는 만들지 않는다.

## 2. 프로젝트

| 논리 속성 | 의미와 규칙 |
| --- | --- |
| 불투명 프로젝트 식별자 | 외부 사용자는 식별자 형식으로 소유자·권한·정렬을 추론할 수 없다. |
| 내부 소유자 참조 | 인증 주체와 연결되며, 서버의 소유권 검사와 Audit 행위자 연결에만 사용한다. |
| 이름 | 공백뿐인 값은 허용하지 않는다. 최대 길이와 정규화 방식은 API 계약 승인 시 정한다. |
| 공통 버전 | 변경 감지 기준이다. 오래된 기준 버전의 쓰기는 저장하지 않고 충돌을 알린다. |
| 현재 단계 | 계획, 역할·환경, 추천, 설정, 완료 같은 업무 흐름의 표시값이 될 수 있다. 이 값만으로 사용자 확정 상태를 판단하지 않는다. |
| 분석·Review 세대 | 비동기 결과가 현재 요청에 속하는지 판단하는 기준이다. 구체 저장 방식은 후속 분석/Review 설계에서 정한다. |
| 생성·갱신 시점 | Audit의 행위 시점과 별개로 프로젝트 생명주기를 설명한다. |

## 3. 프로젝트 하위 리소스

문서 요약, 분석 시도, 기획 초안·확정본, 추천, 미리보기, 승인 등은 모두 정확히 하나의 프로젝트에 귀속된다. 하위 리소스 식별자만으로는 접근할 수 없으며, 요청의 프로젝트와 실제 귀속 프로젝트가 일치해야 한다.

이 기능은 해당 하위 리소스의 상세 스키마를 만들지 않는다. 이후 기능이 추가될 때마다 다음을 만족해야 한다.

1. 프로젝트 소유권 검사를 공통으로 통과한다.
2. 프로젝트 경계가 다른 하위 식별자 조합은 읽기·쓰기 모두 거부된다.
3. 프로젝트 삭제 시 정해진 종속 순서로 프로젝트 종속 A·B 리소스와 Audit을 함께 제거한다. 공용 Catalog와 사용자 계정·Session·사용자 PC 파일은 제거하지 않는다.
4. 비동기 결과는 저장 직전에 프로젝트 존재·상태·버전·세대를 재검증한다.

### 3.1 A6 `ANALYSIS_ATTEMPT`

PDF의 A6는 AI 응답 뒤에만 생기는 로그가 아니라, 분석 시작 시 생성하는 프로젝트 하위 실행 기록이다. 물리 DDL은 후속 승인 대상이지만 다음 논리 속성과 상태 규칙을 보존한다.

| 논리 속성 | 의미와 규칙 |
| --- | --- |
| `id`, `project_id`, `document_id` | 분석 시도 식별자와 프로젝트·문서 요약 귀속이다. |
| `request_id`, `contract_version` | 내부 AI 호출 추적 ID와 AI DTO 계약 버전이다. HTTP body에서는 각각 `requestId`, `contractVersion`으로 camelCase 매핑한다. `request_id`만으로 멱등성을 주장하지 않는다. |
| `generation` | 프로젝트 분석 세대이며 프로젝트 내에서 유일해야 한다. |
| `draft_id_at_start`, `draft_version_at_start` | 호출 시작 시점의 DRAFT 기준이다. |
| `result_draft_id`, `result_draft_version` | 성공 시 실제 반영한 DRAFT 기준이다. |
| `status`, `error_code` | `PROCESSING`, `SUCCEEDED`, `FAILED`와 안전한 실패 코드다. |
| `started_at`, `deadline_at`, `finished_at` | 실행 시작·기한·종료 시각이다. |

분석 시작은 `PROJECT` 잠금 안에서 세대를 증가시키고 A6를 `PROCESSING`으로 기록한 뒤 외부 AI 호출 전에 커밋한다. 이 시점 뒤 실행은 브라우저 HTTP 연결·사용자 세션과 독립된 Core API 소유 작업이며, 로그아웃·세션 만료·연결 종료는 결과 저장의 취소 조건이 아니다. Core API는 `POST /internal/v1/analysis` body에 A6의 `request_id`·`generation`·`contract_version`을 각각 `requestId`·`generation`·`contractVersion`으로 넣고 HMAC으로 서명하며, AI Service는 DB를 만들거나 Spring Session JDBC 테이블을 조회하지 않는다. 이 기능의 AI Service는 업무 결과 대신 `result.kind = CONTRACT_TEST_DOUBLE`만 반환하므로 Core API는 DRAFT·추천 데이터를 만들지 않고 결과 저장 경계만 검증한다. 별도 `AI_REQUEST` 테이블은 추가하지 않는다.

## 4. Audit 이벤트

| 논리 속성 | 의미와 규칙 |
| --- | --- |
| 행위자 참조 | 사용자 작업이면 인증 주체를 가리킨다. 시스템 작업의 표기 규칙은 공통 schema 승인 시 정한다. |
| 프로젝트 참조 | 해당 프로젝트의 보호 작업을 추적한다. |
| 사건·결과·사유 | 상태 변경, 거부, 실패의 최소 의미를 기록한다. |
| 대상 참조 | 작업 대상의 종류와 식별자를 기록한다. |
| 요청·발생 시점 | 요청 상관관계와 발생 시점을 기록한다. requestId만으로 멱등성을 주장하지 않는다. |
| 민감정보 제한 | 원문, Secret, 원시 AI 응답, 인증 token은 기록하지 않는다. |
| 삭제 규칙 | 프로젝트 삭제가 성공하면 프로젝트 종속 Audit도 함께 제거한다. 별도 장기 보관은 추가 정책 승인 없이는 만들지 않는다. |

## 5. 관계와 수명

    인증 주체 1 ── 소유 ── N 프로젝트
    프로젝트 1 ── 귀속 ── N 하위 리소스
    프로젝트 1 ── 기록 ── N Audit 이벤트

프로젝트의 행동 수명은 다음과 같다.

    생성/활성
      ├─ 정상 변경: 소유권 + 기준 버전 확인 → 업무 변경 + 버전 증가 + Audit
      ├─ 충돌: 저장하지 않음 → 최신 정보 재조회 안내
      └─ 삭제 요청: 프로젝트 잠금 + 인증 + 소유권 + 명시적 확인
                      → 활성 역참조 해제 → 종속 데이터와 Audit 제거 → 삭제 완료

삭제 완료 뒤에는 늦은 분석·Review·추천 결과가 도착해도 대상 프로젝트를 새로 만들거나 갱신할 수 없다. 실행 중 작업 자체는 강제 취소하지 않으며, 결과 저장 시 삭제 상태·세대·존재 여부를 다시 확인한다. 공용 Catalog와 사용자 계정·Spring Session JDBC 세션·사용자 PC 파일은 이 삭제 그래프에 포함하지 않는다.

## 6. 물리 설계로 넘기는 결정 목록

- 기존 USER 확장 migration: `github_user_id BIGINT NULL`, `github_login VARCHAR(255) NULL`, null이 아닌 `github_user_id`의 partial unique index 또는 동등한 제약
- Flyway versioned SQL(`services/core-api/src/main/resources/db/migration/`)로 기존 USER 확장과 Spring Session JDBC 기본 schema(`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`) 적용, `AGENTFIT_SESSION` cookie 설정
- 프로젝트 이름 최대 길이와 정규화
- 버전 필드의 물리 타입·갱신 방식·잠금/격리 수준
- 종속 삭제의 FK, cascade, 정리 순서의 구현 방식
- Audit 이벤트의 enum·nullable·보관 정책
- 프로젝트·하위 업무 데이터 migration의 세부 파일 분할과 적용 순서

