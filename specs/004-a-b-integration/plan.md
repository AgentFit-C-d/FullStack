# A-B Phase 2 통합 구현 계획

**브랜치**: `feat/fullstack-a-phase2` | **작성일**: 2026-10-15 | **Spec**: `specs/004-a-b-integration/spec.md`

## 요약

PDF 기준의 A15·B9·공통 Audit 전체 schema를 UUID 기준으로 새 개발 DB에 생성한다. 이후 A가 인증·소유권·현재 basis를 검증하고, 기존 B 추천/Preview 서비스 포트에 A snapshot을 공급한다. 추천·Preview 저장과 Audit, 프로젝트 삭제는 Core API의 하나의 DB transaction 경계에서 처리한다. B 서비스 인터페이스와 DTO는 변경하지 않는다.

## 기술 맥락

- **언어/버전**: Java 21, Spring Boot 3.3.5, Python 3.11+ / FastAPI
- **주요 의존성**: Spring JDBC, Spring Transaction, Flyway, 기존 HMAC AI client, JUnit/MockMvc
- **저장소**: PostgreSQL. 개발 DB는 기존 데이터를 보존하지 않고 UUID 기준으로 새로 생성
- **Migration 실행 전략**: Phase 2는 기존 V1~V4 개발 DB를 폐기한 뒤 새 빈 개발 DB에 전체 UUID schema migration을 적용한다. 기존 DB의 in-place 변환이나 데이터 mapping은 수행하지 않는다.
- **테스트**: Maven Surefire, JUnit, MockMvc, PostgreSQL/Testcontainers(가능한 환경), 기존 AI pytest
- **대상**: Spring Boot Core API
- **구조**: A/B는 동일 Core API 애플리케이션 내부 모듈이며 FastAPI는 AI 처리만 담당
- **성능**: 외부 AI 호출은 transaction 밖에서 수행. 기존 분석 timeout 600초 정책을 유지하되 운영 증명 전에는 확정 성능으로 표시하지 않음
- **제약**: B 서비스 인터페이스·DTO 변경 금지, 원문/Secret/raw AI response의 DTO·Audit·일반 로그 복제 금지, 소유권·basis·Catalog 오류 fail-closed
- **범위**: A15 + B9 + Audit schema, 추천 생성/최신 조회, Preview 생성, 프로젝트 삭제 transaction

## 헌장 검증

- 단일 DB 쓰기 소유권: **통과** — Core API만 PostgreSQL을 사용한다.
- AI 제안과 사용자 확정 분리: **통과** — AI 결과는 검증된 추천 입력일 뿐 확정값이 아니다.
- 인증·소유권·버전·잠금: **통과** — A snapshot reader와 저장 직전 재검증이 담당한다.
- Audit 원자성: **통과** — 성공 업무 상태와 Audit을 같은 transaction에서 저장한다.
- 계약 우선: **통과 조건** — OpenAPI, DTO, 서버 검증, 정상/실패 fixture를 같은 변경으로 갱신한다.
- UUID 전환: **통과 조건** — 기존 개발 DB를 보존하지 않는 초기화 migration으로 수행한다.
- TDD/독립 리뷰: **통과 조건** — 각 핵심 규칙에 실패 테스트를 먼저 추가하고 전체 suite와 독립 리뷰를 실행한다.

## 설계 산출물

```text
specs/004-a-b-integration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/ab-api.md
└── tasks.md
```

## 소스 구조 및 책임

```text
services/core-api/src/main/resources/db/migration/
├── V1~V4 기존 기반
└── V5__agentfit_ab_uuid_schema.sql        # 새 개발 DB용 A15+B9+Audit 전체 schema

services/core-api/src/main/java/com/agentfit/coreapi/
├── domain/auth/                            # USER/SESSION, 인증 주체
├── domain/project/                         # PROJECT, 소유권, 삭제 transaction
├── domain/analysis/                        # 분석 시도와 AI gateway
├── integration/ab/                         # A snapshot ↔ B port adapter
├── catalog/                                # B Catalog/추천/Preview workflow
├── recommendation/                         # B 입력·판정·선택 규칙
└── configuration/persistence/               # B 저장/삭제 JDBC

services/core-api/src/test/java/com/agentfit/coreapi/
├── contract/                               # OpenAPI/경계 계약
├── integration/ab/                         # A-B adapter transaction 테스트
└── domain/project/                         # 삭제 순서·late result 테스트
```

## 구현 단계

### 1단계 — UUID 전체 schema

1. PDF의 A15, B9, Audit 컬럼·상태·nullable·복합 unique/FK를 최종 목록으로 고정한다.
2. 기존 개발 DB를 폐기하고 새 빈 개발 DB를 생성한 뒤, 전체 schema migration을 적용하는 실행 절차를 고정한다.
3. 위 실행 전략에 맞춰 `V5__agentfit_ab_uuid_schema.sql`을 작성한다. 기존 DB 변환용 compatibility migration은 추가하지 않는다.
4. `PROJECT.current_review_id` 등 순환 역참조는 삭제 전 해제할 수 있도록 설계한다.
5. FK 삭제 cascade에 의존하지 않고 PDF의 명시적 삭제 순서를 검증하는 integration test를 작성한다.

### 2단계 — A snapshot과 추천 연결

1. 실패 테스트로 인증 실패, 타 프로젝트, 미확정 Profile, stale basis, 허용되지 않은 source field를 고정한다.
2. 기존 `RecommendationCreationService.OwnerCheckedSnapshotReader`를 A가 구현한다.
3. 기존 `CapabilityAnalyzer`에 기존 AI gateway 응답을 검증된 `AiCapabilityIntake`로 변환한다.
4. 기존 `RecommendationStore`를 A transaction/JDBC writer에 연결한다.
5. Controller 구현 전에 `POST /api/projects/{projectId}/recommendations`와 `GET /api/projects/{projectId}/recommendations/latest`의 request/response, nullable, 오류 envelope, stale/ownership/AI failure fixture를 OpenAPI에 확정한다.

### 3단계 — Preview 연결

1. 추천 소속, project/basis version, Environment, installed component, Catalog hash 검증 실패 테스트를 먼저 작성한다.
2. 기존 `PreviewCreationService.TrustedContextReader`와 `PreviewStore`를 A snapshot/JDBC 저장 경계에 연결한다.
3. 생성된 bytes가 아니라 B가 허용한 metadata·fingerprint만 저장한다.
4. Controller 구현 전에 `POST /api/projects/{projectId}/config-previews`의 request/response, nullable, 오류 envelope, Catalog/basis mismatch fixture를 OpenAPI에 확정한다.

### 4단계 — Audit 및 삭제 transaction

1. 추천/Preview 성공과 Audit 성공이 함께 commit되는지, Audit 실패 시 함께 rollback되는지 검증한다.
2. PROJECT lock → active-operation check → B export/preview/selection/recommendation → B Audit → A profile/review/analysis/environment → A Audit → PROJECT reference release → PROJECT 삭제 순서를 하나의 caller-owned transaction으로 구현한다.
3. active analysis, FK failure, lock conflict, 삭제 후 late result을 검증한다.
4. 업무 rollback 이후 별도 최소 Audit transaction으로 실패·거부 사건을 기록하고 raw input/Secret/raw AI response가 저장되지 않는지 검증한다.
   - 대상: 인증 실패, 소유권·참조 불일치, 삭제 프로젝트 접근, stale/version 충돌, 미확정·불완전·UNDETERMINED 분석, AI 호출·응답 검증 실패, Catalog unavailable/hash 불일치/지원되지 않는 조합, 활성 작업 삭제 거부, 삭제 transaction/FK/lock 실패.

### 5단계 — 전체 검증

1. Core API 전체 Maven suite와 가능한 PostgreSQL integration suite를 실행한다.
2. OpenAPI·migration·DTO·서비스 interface 간 diff를 검사한다.
3. 현재 baseline의 기존 실패와 이번 변경의 실패를 구분해 기록한다.
4. 기존 B public interface/DTO signature diff는 실제 `catalog/`, `recommendation/`, `configuration/preview/` package 기준으로 검사한다.

## 주요 리스크 및 대응

- **PDF와 기존 A 타입 불일치**: 기존 DB 보존을 요구하지 않으므로 UUID 초기화 migration으로 정리한다.
- **AI Capability 누락 의미 불일치**: 계약이 확정되기 전에는 B의 fail-closed 검증을 유지하고 성공 추천으로 승격하지 않는다.
- **외부 AI와 transaction 결합 위험**: AI 호출은 transaction 밖에서 수행하고 저장 직전 basis를 재검증한다.
- **삭제 순서와 FK 충돌**: B deleter는 caller-owned transaction을 요구하므로 A가 outer transaction을 소유한다.
- **실패 Audit 원자성**: 업무 transaction rollback 이후 별도 최소 Audit transaction을 사용하고 민감정보 저장을 차단한다.

## 검토 초점

- 타 사용자 project/child ID는 조회·변경·Audit 대상이 되지 않아야 한다.
- 기존 CONFIRMED/성공 Review/기존 추천은 AI 실패나 늦은 응답으로 훼손되지 않아야 한다.
- UUID migration은 모든 A/B FK와 세션 참조 타입을 일관되게 만들어야 한다.
- 성공 업무 상태와 Audit은 항상 함께 commit되거나 함께 rollback되어야 한다.
- 삭제 후 늦은 분석/추천 결과가 어떤 row도 재생성하지 않아야 한다.

