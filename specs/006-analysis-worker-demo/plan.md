# 구현 계획: 분석 Worker 연결과 A→확인→B 데모 흐름 완성

**대상 브랜치**: `codex/analysis-worker-demo` | **날짜**: 2026-10-17 | **명세**: [spec.md](spec.md)

**입력**: `specs/006-analysis-worker-demo/spec.md`

## 요약

Core API가 `PROCESSING` 분석 시도를 실제로 처리하는 주기 Worker를 추가한다. Worker는
Core가 소유한 PlanningInput을 잠시 읽어 HMAC으로 AI 서비스에 전달하고, 응답을 검증한 뒤
PROJECT 잠금과 expected version/basis 재검증을 거쳐 구조화된 A DRAFT·성공 상태·원문 삭제를 원자적으로 마무리한다. 공급자·계약·저장 실패는
자동 재시도하지 않고 `FAILED`로 끝낸다. PostgreSQL의 UUID/status 기준을 Repository와
관련 A 테이블뿐 아니라 인증 principal·Project·PlanningInput·Audit의 Core A 요청 경로에 맞추고,
Docker의 analysis/needs/rank/Solar 환경값을 연결한다.

## 기술 환경

**언어/버전**: Java 21 / Spring Boot 3.3.5, Python 3.11 이상 / FastAPI
**주요 의존성**: Spring Web·Scheduling·JDBC·Flyway·Jackson, PostgreSQL JDBC,
FastAPI·pytest, 기존 HMAC AI client
**저장소**: PostgreSQL 17은 Core API만 읽기·쓰기한다. PlanningInput의 원문·추출문은
분석 terminal 처리까지의 임시 데이터이며, DRAFT·확정 Profile·감사 메타데이터만 유지한다.
**테스트**: JUnit·MockMvc·JDBC·PostgreSQL 통합 테스트, pytest/TestClient, Docker Compose
smoke test와 Solar 1회 수동 smoke test
**대상**: Docker Desktop Linux 엔진의 `core-api`·`ai-service`·`postgres`와 내부 HMAC HTTP 통신
**프로젝트 유형**: Spring Boot Core API + FastAPI 계산 서비스의 백엔드 통합
**성능 목표**: 데모 기본 Worker polling 주기는 1초이며, 새 `PROCESSING` attempt는 테스트 환경에서 2초 안에 claim된다. claim lease 기본값은 180초 이상으로 두며, AI 실행은 기존 Core deadline과 178초 상한을 넘지 않는다.
**제약**: 외부 AI 호출은 DB 트랜잭션 밖에서 1회만 수행한다. AI는 DB·권한·문서 수명을
소유하지 않는다. 원문·전체 추출문·Secret·정상 원시 AI 응답은 로그·DTO·Audit·장기 저장소에
남기지 않는다.
**범위**: A Worker, PostgreSQL A 의존 테이블 정합성, Docker 런타임 값, A→확인→B의
백엔드 데모를 포함한다. 프론트엔드, 메시지 큐, 다중 Worker, 자동 재시도, `rank`의 최종
추천 반영, 실제 Catalog 준비는 제외한다.

## 헌장 점검

*Gate: Phase 0 전과 Phase 1 후 모두 통과해야 한다.*

| 점검 | 설계 대응 | 결과 |
|---|---|---|
| Core API 단일 DB 소유 | Worker·Repository·DRAFT·원문 삭제는 Core에만 두고 AI는 HMAC 요청/응답만 처리 | 통과 |
| AI 제안과 사용자 확정 분리 | Worker는 DRAFT만 저장하며 기존 확인 트랜잭션이 CONFIRMED Profile을 생성 | 통과 |
| 원문·Secret 보호 | 입력은 메모리에서 1회 전달하고 모든 terminal 경로에서 scrub; Solar 키는 Compose 환경변수로만 주입 | 통과 |
| 동시성·지연 결과 방지 | UUID attempt, 실행 claim token, PROJECT 잠금, expected version/basis·deadline·generation 재검증, terminal 조건부 갱신을 사용 | 통과 |
| 계약 우선 | 기존 Core 공개 API와 AI OpenAPI 경로를 유지하고 Docker URL/환경계약을 문서화·테스트 | 통과 |
| 작은 변경과 테스트 | 기존 `AnalysisExecutionService`, `ProjectAnalysisService`, Repository, Scheduler를 확장; 새 서비스 분리·큐 도입 없음 | 통과 |

## 프로젝트 구조

### 문서 (이번 기능)

```text
specs/006-analysis-worker-demo/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── analysis-worker.md
│   └── docker-runtime.md
├── tasks.md
└── review.md                         # 구현 뒤 독립 코드 리뷰 기록
```

### 소스 코드 (저장소 루트)

```text
services/core-api/
├── src/main/java/com/agentfit/coreapi/domain/analysis/
│   ├── entity/                         # AnalysisAttempt UUID·실행 claim 모델
│   ├── repository/                     # PostgreSQL claim/terminal SQL
│   ├── service/                        # Worker·실행·결과 저장 조정
│   ├── client/                         # 기존 HMAC analysis/needs/rank client
│   └── config/                         # AI URL·Worker 설정
├── src/main/java/com/agentfit/coreapi/domain/project/
│   ├── repository/                     # PlanningInput 조회·scrub
│   └── service/                        # 분석 시작·확정 흐름
├── src/main/resources/db/migration/    # fresh PostgreSQL 정합성 migration
└── src/test/java/com/agentfit/coreapi/domain/analysis/
    ├── ...WorkerTest.java
    └── ...PostgresIntegrationTest.java
services/ai-service/
├── app/main.py                          # Solar provider 선택과 HMAC 라우트
└── tests/                               # Solar provider 선택·계약 회귀
infra/compose.fullstack.yaml             # 세 AI URL·Solar 환경 주입
.env.docker.example                      # 비밀값 없는 Docker 환경 템플릿
contracts/openapi/                       # 기존 공개/Core↔AI API 계약
```

**구조 결정**: A와 B는 기존 단일 Core API에 남긴다. Worker도 Core 안의
`@Scheduled` 컴포넌트이며, FastAPI나 별도 큐 서비스에 DB 책임을 추가하지 않는다.

## 구현 순서

1. fresh PostgreSQL에서 V5 UUID 로그인 사용자부터 Project·PlanningInput·분석 시작까지의
   경로와 A 의존 테이블·Java SQL이 어긋나는 지점을 migration contract test로 먼저 재현한다.
2. UUID `AnalysisAttempt`와 `status`/`finished_at` 기준으로 Repository·DTO·상태 전이 SQL을
   교정하고, expected project version/basis, PlanningInput·admission·Audit의 V5 이후 의존 구조를 migration으로 보완한다.
3. PROCESSING row를 조건부로 claim하는 Worker를 연결한다. AI 호출은 transaction 밖에서 한 번만
   실행하고, 성공 terminal 저장 직전에는 PROJECT를 잠근 뒤 version/basis·generation·deadline을 재검증한다. 성공 또는 실패 terminal 작업은 Core 트랜잭션에서 처리한다.
4. Docker Compose에 세 AI URL과 Solar provider/key 전달을 추가하고, `.env.docker.example`에는
   키 이름만 추가한다.
5. 단위·PostgreSQL·HMAC 계약·Compose smoke·실제 Solar 텍스트 smoke를 순서대로 실행한다.
   마지막으로 기존 확인 API와 B 추천 API를 사용해 정상 B 상태를 확인한다.

## 복잡성 추적

| 위반 | 필요 이유 | 더 단순한 대안을 거절한 이유 |
|---|---|---|
| 없음 | - | - |
