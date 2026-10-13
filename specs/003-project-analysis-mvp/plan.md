# Implementation Plan: 프로젝트 생성과 분석 시작 MVP

**Branch**: `003-project-analysis-mvp` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: 기능 명세와 2026-10-13 확정사항(원문 0일 보관, 600초 만료, 동시 제한, 생성 멱등성)

## Summary

기존 Spring Boot Core API에서 인증된 사용자가 역할·런타임·기획 입력을 포함한 프로젝트를 만들고, 저장된 메타데이터를 다시 조회·수정하며, 분석을 `PROCESSING`으로 접수할 수 있게 한다. PDF/Markdown 파일 또는 UTF-8 일반 텍스트를 multipart로 검증하고, 원문은 terminal 상태 직후 삭제한다. PostgreSQL의 행 잠금·partial unique index·admission singleton으로 프로젝트 1건/사용자 1건/서버 2건 제한을 원자적으로 보장한다.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.5, Spring MVC/Validation/Security/JDBC, Spring Session JDBC, Flyway, PostgreSQL JDBC, Apache PDFBox(신규; PDF 페이지·텍스트 검증), Testcontainers PostgreSQL(신규 test scope; PostgreSQL 동시성 검증)

**Storage**: PostgreSQL 17 계열; Flyway V4 additive migration. 입력 원문은 `BYTEA`/`TEXT`에 terminal 상태 전까지만 두고 메타데이터·Attempt는 보존한다.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, 순수 단위 테스트, Testcontainers PostgreSQL 통합 테스트

**Target Platform**: 세션 기반 same-origin REST Core API. 프런트엔드는 아직 없으므로 이번 계획은 API·계약·서버 검증 범위다.

**Project Type**: 웹 백엔드 서비스

**Performance Goals**: 서버 전체 활성 분석 2건을 절대 넘기지 않고, 만료 Attempt를 600초 기한 뒤 회수한다.

**Constraints**: PDF/Markdown 파일 최대 10 MiB, PDF 100쪽, 추출/직접 텍스트 100,000 Unicode code points, runtime 최대 30개·각 200 code points, 프로젝트 이름 100 code points, 원문 보관 기간 0일, 동일 생성 키 재시도 시 중복 생성 금지.

**Scale/Scope**: 개인 소유 프로젝트만 지원한다. 공유·AI 결과 생성·프런트엔드·외부 파일 저장소는 제외한다.

## Constitution Check — 설계 전

| 헌장 원칙 | 판정 | 계획 반영 |
|---|---|---|
| I. Core API 단일 쓰기 소유권 | 통과 | Core API만 PostgreSQL을 변경하며 AI 호출은 분석 시작에서 수행하지 않는다. |
| II. 원천 정보 분리 | 통과 | 원문·추출문·인용문은 DTO/Audit/일반 로그에서 제외하고 terminal 처리 때 삭제한다. |
| III. 소유권 검증 | 통과 | 모든 project-scoped endpoint는 `SessionPrincipalResolver`와 `ProjectOwnershipGuard`를 사용한다. |
| IV. 버전·경쟁·실패 보존 | 통과 | `expectedVersion`, `SELECT ... FOR UPDATE`, partial unique index, admission 행, 조건부 terminal 전환을 사용한다. |
| V. 업무 상태·Audit 원자성 | 통과 | 생성·수정·입력 교체·분석 시작·만료 정리와 최소 Audit을 하나의 트랜잭션으로 처리한다. |
| VI. 계약 우선 | 통과 | OpenAPI, DTO, 오류 코드, MockMvc/계약 테스트를 같은 작업에서 바꾼다. |
| VII. 작은 변경·실패 테스트·독립 리뷰 | 통과 | 기존 `ProjectService`를 확장하고, 새 서비스는 분석 lifecycle 하나로 제한하며 단위·웹·PostgreSQL 테스트와 후속 독립 리뷰를 둔다. |

**게이트 결과**: 통과. PDFBox와 Testcontainers는 각각 요구된 PDF 한도 검증과 PostgreSQL 경쟁 규약 검증에 필요한 최소 의존성이다.

## 설계

### HTTP와 입력 경계

1. `POST /api/projects`는 `Idempotency-Key` 및 CSRF 헤더가 있는 `multipart/form-data`만 받는다.
2. `metadata` JSON 파트는 `name`, `role`, `runtimeInput`을 가진다.
3. `document` 파일(PDF 또는 Markdown)과 `inputText`(`text/plain`) 중 하나만 받는다.
4. `PATCH /api/projects/{projectId}`는 이름·role·runtime과 `expectedVersion`만 바꾼다.
5. `PUT /api/projects/{projectId}/planning-input`는 새 입력 revision을 받는다.
6. `POST /api/projects/{projectId}/analysis-attempts`는 `expectedVersion`을 받고 `202 PROCESSING`을 반환한다.

정확한 오류와 요청·응답 모양은 [contracts/project-analysis-mvp.md](contracts/project-analysis-mvp.md)를 기준으로 `contracts/openapi/core-api.yml`에 반영한다.

### 도메인과 트랜잭션 경계

- `ProjectService`: 프로젝트 생성(멱등성 예약 포함), 상세 조합, 정보 수정, 입력 교체를 처리한다. 기존 CRUD를 이 서비스에 유지한다.
- `ProjectAnalysisService`: 분석 접수, 동시성 admission, 600초 만료 정리를 처리한다. 실제 AI 호출/결과 생성은 수행하지 않는다.
- `ProjectRepository`는 기존 project row 잠금과 version 갱신을 유지·확장한다.
- `PlanningInputRepository`, `ProjectRuntimeRepository`, `ProjectCreationIdempotencyRepository`, `AnalysisAdmissionRepository`는 SQL 저장소 역할만 가진다.
- `AnalysisAttemptRepository`는 Attempt 시작, active count 조회, 조건부 terminal 전환을 지원한다.
- `AuditRecorder`의 차단 키 목록을 원문·추출문·인용문 관련 키까지 확대한다. Audit은 action/result/reason만 남긴다.

생성·수정·입력 교체·분석 시작은 각각 프로젝트 행을 잠그고 version을 검사한다. 분석 시작과 terminal 정리는 같은 잠금 순서(`PROJECT` → `ANALYSIS_ADMISSION`)를 사용해 서버 전체 2건 제한을 보장한다.

### 600초 만료와 원문 삭제

`@EnableScheduling`을 켜고 최대 10초 간격의 고정 지연 스케줄러를 추가한다. 이번 MVP에서 구현하는 terminal 상태는 timeout뿐이다. 만료 후보를 찾은 뒤 각각의 Attempt를 `PROCESSING` 상태 조건으로 terminal 전환한다. 성공한 전환에만 입력 원문·추출문 삭제, admission 1 감소, `ANALYSIS_TIMEOUT` Audit 기록을 실행한다. 이 종료·삭제 연산은 후속 성공·실패·취소 경로도 재사용할 하나의 내부 lifecycle 연산으로 만든다. Clock을 주입해 테스트가 실제 600초를 기다리지 않게 한다.

### 활성 분석 중 프로젝트 삭제

`PROCESSING` Attempt가 있으면 프로젝트 삭제를 `409 ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN`으로 거부한다. Attempt를 cascade 삭제하면 admission count가 누수될 수 있으므로, 활성 분석을 강제 삭제하거나 terminal 전환하지 않는다. terminal 상태가 된 뒤에는 기존 002 프로젝트 삭제 규약에 따라 종속 데이터를 제거한다.

### 멱등성

생성 트랜잭션은 `(owner, PROJECT_CREATE, Idempotency-Key)`를 먼저 예약한다. 같은 키·같은 fingerprint이면 연결된 기존 project detail을 반환한다. 같은 키·다른 fingerprint는 `409 IDEMPOTENCY_KEY_REUSED`이며, 새 프로젝트나 입력은 만들지 않는다. 실패한 생성은 키 예약까지 롤백한다.

## Project Structure

### Documentation (this feature)

```text
specs/003-project-analysis-mvp/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── project-analysis-mvp.md
```

### Source Code (repository root)

```text
services/core-api/
├── pom.xml
├── src/main/java/com/agentfit/coreapi/
│   ├── CoreApiApplication.java
│   ├── global/exception/
│   ├── domain/audit/
│   ├── domain/project/
│   │   ├── controller/ProjectController.java
│   │   ├── dto/request/                 # create/update/input/start DTO
│   │   ├── dto/response/                # profile/input/attempt-safe response
│   │   ├── entity/                      # Project와 입력·runtime 값 객체
│   │   ├── repository/                  # project/runtime/input/idempotency JDBC
│   │   └── service/ProjectService.java
│   └── domain/analysis/
│       ├── entity/AnalysisAttempt.java
│       ├── repository/AnalysisAttemptRepository.java
│       └── service/ProjectAnalysisService.java
├── src/main/resources/db/migration/
│   └── V4__project_analysis_mvp.sql
└── src/test/java/com/agentfit/coreapi/
    ├── contract/
    └── domain/{project,analysis}/

contracts/openapi/core-api.yml
```

**Structure Decision**: 기존 단일 Core API의 도메인별 패키지 구조를 그대로 사용한다. 프로젝트 CRUD는 `ProjectService`에, 분석 lifecycle은 `ProjectAnalysisService` 하나에 둔다. 프런트엔드 디렉터리나 별도 마이크로서비스는 만들지 않는다.

## 구현 순서

1. **계약·오류·의존성 기반 마련**: PDFBox/Testcontainers를 추가하고, OpenAPI를 0.2.0으로 올려 기존 JSON 프로젝트 생성에서 multipart 생성으로의 의도된 breaking change를 기록한다. DTO·오류 코드를 새 multipart/수정/입력/분석 계약과 일치시키고, 기존 OpenAPI의 세션 쿠키 `Path=/api` 설명은 실제 `SecurityConfig`의 `Path=/`와 일치시킨다.
2. **V4 스키마와 JDBC 저장소**: 역할, runtime, 입력 메타데이터/원문, 멱등성, admission, Attempt 확장 및 프로젝트 삭제 cascade를 추가한다. 기존 V1~V3은 변경하지 않는다.
3. **입력 검증과 프로젝트 CRUD 확장**: 파일 형식·크기·PDF 페이지·텍스트 한도, runtime 정규화, 생성 멱등성, 정보 수정, 상세 재조회를 `ProjectService`에서 구현한다.
4. **분석 접수와 경쟁 제어**: 소유권·expectedVersion 검증, 동일 프로젝트 `409`, 사용자/서버 `429`, `PROCESSING` 생성, attempt metadata 응답을 구현한다.
5. **만료 정리·원문 삭제**: 최대 10초 간격의 Clock 기반 600초 timeout, 재사용 가능한 조건부 terminal 전환, admission 회수, raw scrub, 최소 Audit을 구현한다.
6. **테스트와 계약 검증**: 아래 Review Focus를 단위·MockMvc·PostgreSQL 통합 테스트로 고정하고 Maven 전체 테스트를 실행한다.

## Review Focus

1. **동시 멱등성 키 재시도**: 같은 키의 두 생성 요청이 동시에 와도 하나의 프로젝트/응답만 생기고, 다른 본문 재사용은 `409 IDEMPOTENCY_KEY_REUSED`인지 PostgreSQL 통합 테스트에서 확인한다.
2. **동시 분석 시작 경합**: 동시에 세 요청이 와도 서버 전체 `PROCESSING`이 두 건을 넘지 않는지 PostgreSQL 통합 테스트에서 확인한다.
3. **만료 정리 중복 실행**: 두 스케줄러 실행 또는 지연 실행에도 admission count가 음수로 내려가지 않고 원문 삭제가 안전한지 lifecycle 테스트에서 확인한다.
4. **PDF 경계값**: 10 MiB, 100쪽, 100,000 code point 바로 안/밖 입력을 validation 테스트에서 확인한다.
5. **상세 응답 유출**: 종료된 입력의 raw/추출문, ownerId, Audit metadata에 원문 키가 노출되지 않는지 MockMvc·Audit 테스트에서 확인한다.
6. **활성 분석 중 삭제**: `PROCESSING` 프로젝트 삭제가 409으로 거부되고 Attempt·원문·admission count가 변하지 않는지 삭제 통합 테스트에서 확인한다.

## Constitution Check — 설계 후

| 항목 | 결과 |
|---|---|
| 데이터 단일 쓰기 소유권 | Core API JDBC 저장소만 DB를 변경하므로 통과 |
| 원문 비복제·종료 삭제 | 계약·DTO·Audit 차단·scrub 트랜잭션에 반영되어 통과 |
| 소유권·버전 경계 | 모든 project-scoped endpoint와 write에 반영되어 통과 |
| 다중 인스턴스 동시성 | PostgreSQL partial unique index와 admission row 잠금으로 통과 |
| 계약 정합성 | OpenAPI·DTO·오류·테스트 동시 변경으로 통과 |
| TDD·독립 리뷰 | 테스트 선행과 구현 후 독립 리뷰가 다음 task 단계의 완료 조건이므로 통과 |

## Complexity Tracking

헌장 위반 없음. 별도 마이크로서비스, 메시지 큐, 외부 파일 저장소, 추가 CRUD 서비스 분리는 사용하지 않는다.
