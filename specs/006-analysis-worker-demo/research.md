# 조사 결과: 분석 Worker와 PostgreSQL 데모

## 결정 1: V5의 UUID/status를 PostgreSQL 기준으로 유지하고 Core A의 UUID 경로를 완성한다

**결정**: `analysis_attempt.id`는 UUID, 상태 컬럼은 `status`, 종료 시각은 `finished_at`를
기준으로 한다. 인증 principal부터 Project 소유권·생성·조회, 생성 idempotency, PlanningInput,
Audit까지 Core A의 요청 경로도 UUID만 사용한다. 현재 `V5__agentfit_ab_uuid_schema.sql` 뒤에
새 migration을 추가해 Worker에 필요한 실행 claim 정보와 V5가 삭제한 A 의존 테이블을 UUID
foreign key로 보완한다. Repository·entity·DTO는 이 구조로 맞춘다.

**근거**: 현재 V5는 이전 BIGSERIAL/state 테이블을 삭제하고 UUID/status 테이블을 새로 만든다.
반면 Java Repository와 서비스 일부는 `Long id`, 숫자 principal, `owner_id`, 이전 `PROJECT`
테이블, `state`, `completed_at`, `input_revision`을 조회한다. 또한 V5 이후에는
`planning_input`, `analysis_admission`, UUID 기반 생성 idempotency가 없어 A 분석 시작과 원문
scrub이 fresh PostgreSQL에서 동작하지 않는다. V5를 기준으로 후속 migration과 Core A UUID
refactor를 함께 수행해야 fresh DB와 실제 요청 경로가 일치한다.

**대안**:

- Java 코드를 유지하고 V5를 BIGSERIAL/state로 되돌리기: UUID 기반 A/B schema와 충돌한다.
- UUID와 숫자 ID의 fallback을 새 데모 경로에 계속 남기기: 어느 SQL이 실행되는지 환경에 따라 달라져 fresh PostgreSQL 오류를 숨긴다.
- V5를 직접 다시 작성하기: 이미 적용된 Flyway 이력과 새 DB 재현성을 훼손한다.
- H2만 계속 사용하기: 실제 PostgreSQL 오류를 숨긴다.

## 결정 2: 주기 Worker는 durable claim을 가진 PROCESSING 시도만 한 번 실행한다

**결정**: Worker는 설정 가능한 짧은 주기로 unclaimed `PROCESSING` 시도를 조건부 claim한다.
claim token과 lease 시간은 DB에 저장하고, terminal 전환은 같은 token을 가진 Worker만 할 수
있다. Worker가 AI 호출 중 비정상 종료되어 claim이 만료되면 다른 Worker가 같은 외부 AI 호출을
다시 하지 않고 해당 시도를 `FAILED/WORKER_INTERRUPTED`로 끝낸다. 사용자는 새 분석을 요청한다.

**근거**: 단순 `SELECT` 뒤 호출은 여러 인스턴스가 같은 AI 요청을 보내게 한다. provider 실패와
중간 프로세스 종료 모두 자동 재호출하면 비용과 중복 DRAFT 위험이 생긴다. claim은 동시성만
막고, 만료 claim의 실패 종료는 사용자가 확정한 “자동 재시도 없음” 정책을 지킨다.

**대안**:

- 메모리 `AtomicBoolean`만 사용: 재시작·다중 인스턴스에서 안전하지 않다.
- lease 만료 후 같은 분석을 재호출: 자동 재시도 금지와 비용 통제에 맞지 않는다.
- 별도 메시지 큐: 데모 범위를 넘으며 이후 확장 항목이다.

## 결정 3: AI 호출은 transaction 밖에서 실행하고 terminal 성공/실패만 Core transaction으로 묶는다

**결정**: Worker는 claim과 입력 snapshot 조회를 짧은 transaction으로 처리하고, HMAC AI 호출은
transaction 밖에서 한 번 실행한다. 성공 시에는 project·generation·deadline·claim token을
재검증한 뒤 DRAFT 저장, attempt 성공 전환, PlanningInput scrub, admission 해제, Audit을 하나의
Core transaction으로 처리한다. 실패도 조건부 terminal 전환, scrub, admission 해제, 최소 Audit을
한 transaction으로 처리한다.

**근거**: AI 대기 시간 동안 DB lock을 잡으면 다른 프로젝트 작업과 timeout 처리가 막힌다.
반대로 terminal 작업이 흩어지면 DRAFT만 남거나 원문만 남는 반쪽 상태가 생긴다.

**대안**:

- 전체 작업을 하나의 transaction으로 수행: 외부 호출 동안 lock을 유지한다.
- DRAFT 저장 후 별도 cleanup: 저장 실패·재시작에서 원문 수명 보장이 깨진다.

## 결정 4: AI 서비스 503도 Worker 차원의 자동 재호출 없이 실패로 끝낸다

**결정**: `AnalysisExecutionService`의 `NotAcceptedException` 즉시 1회 재호출 동작을 제거한다.
503, transport, provider, 계약, 결과 저장 오류는 정규화해 `FAILED`와 최소 error code로 남긴다.

**근거**: 원본 AI v2는 HTTP transport retries를 0으로 두며, 사용자는 데모에서 자동 재시도를
원하지 않는다고 확정했다. 하나의 분석 시도가 여러 유료 Solar 호출을 만드는 것을 막는다.

**대안**:

- 503만 1회 재시도: 비용·중복 호출·원문 수명 관리가 복잡해진다.
- 지수 백오프 큐: 운영 확장 항목으로 보류한다.

## 결정 5: Docker는 URL과 Solar 키를 명시적으로 컨테이너에 전달한다

**결정**: Core에는 `AGENTFIT_AI_SERVICE_ANALYSIS_URL`, `AGENTFIT_AI_SERVICE_NEEDS_URL`,
`AGENTFIT_AI_SERVICE_RANK_URL`을 모두 `ai-service:8001`로 설정한다. AI 컨테이너에는
`AGENTFIT_AI_V2_PROVIDER`와 `UPSTAGE_API_KEY`를 Compose substitution으로 전달한다.
`.env.docker.example`에는 변수 이름과 빈 안내만 두며 실제 `.env.docker`만 키 값을 가진다.

**근거**: 현재 Compose는 analysis URL의 옛 별칭만 주입해 needs/rank가 컨테이너 자신의
`localhost:8000`으로 향할 수 있고, Solar 변수도 AI 컨테이너에 전달하지 않는다.

**대안**:

- `.env.docker`만 작성: Compose가 AI 컨테이너에 전달하지 않으므로 부족하다.
- 키를 Compose에 직접 기록: 저장소·이미지·로그 노출 위험이 있다.

## 결정 6: A 인계 데모의 B 성공은 정상 상태 구분이다

**결정**: A DRAFT 확정 뒤 실제 B API를 호출해 `RECOMMENDED`, `NEEDS_INFORMATION`,
`NO_COMPATIBLE_TOOLS` 중 데이터와 Catalog에 맞는 정상 결과를 확인하면 통과다. 실제 도구 목록은
Catalog를 소유한 B 담당자의 준비 상태에 따라 선택적으로 확인한다.

**근거**: A의 책임은 확정 Profile과 AI capability 연결이 B로 전달되는 것까지다. 실제 Catalog가
없어 `NO_COMPATIBLE_TOOLS`가 나오는 것은 AI·A 연동 실패와 다르다.

**대안**:

- `RECOMMENDED`만 통과: B Catalog 미준비 때문에 A 인계가 막힌다.
- B 호출 제외: A→확인→B 연결을 검증할 수 없다.
