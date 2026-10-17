# Feature Specification: 분석 Worker 연결과 A→확인→B 데모 흐름 완성

**Feature Branch**: `codex/analysis-worker-demo`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "분석 Worker 연결, PostgreSQL 정합성 수정, Docker 데모 설정을 완료하고 A 분석→DRAFT 저장→원문 삭제→사용자 확인→B 추천 흐름을 실제로 성공시킨다."

**Related Feature**: `specs/005-ai-v2-core-integration`

## Clarifications

### Session 2026-10-17

- Q: AI 호출이 일시적으로 실패하면 서버가 자동 재시도할까요? → A: 이번 데모에서는 자동 재시도 없이 `FAILED`로 종료하고, 사용자가 새 분석 요청을 한다. 운영 안정화 뒤 자동 재시도 정책은 별도 후속 작업으로 검토한다.
- Q: 실제 PostgreSQL의 분석 시도 구조는 migration에 있는 `UUID id`와 `status`를 기준으로 고정할까요? → A: migration의 `UUID id`와 `status`를 기준으로 고정하고, Repository와 테스트를 이 구조에 맞춘다.
- Q: V5 UUID schema와 이전 숫자 ID Core A 코드가 섞여 있을 때 어느 쪽을 기준으로 할까요? → A: V5 UUID schema를 기준으로 고정한다. 인증 principal, Project 소유권·조회, Project 생성 idempotency, PlanningInput 조회·저장·scrub, Audit의 Core A 경로는 UUID만 사용하며 숫자 ID fallback SQL은 새 PostgreSQL 데모 경로에서 사용하지 않는다.
- Q: 풀스택 B에 넘기기 전 Docker 데모에서 Solar를 실제로 한 번 호출해 성공해야 하나요? → A: 결정적 제공자로 반복 테스트하고, 작은 텍스트 입력으로 Solar 실제 호출도 1회 성공해야 한다.
- Q: 데모용 Worker는 일정 주기로 `PROCESSING` 건을 찾아 처리하는 단순 방식으로 할까요? → A: 주기 Worker가 `PROCESSING` 건을 조회·처리한다. 추후 메시지 큐, 다중 Worker, 명시적 재시도 정책으로 확장할 수 있게 상태·처리권 경계는 유지한다.
- Q: 풀스택 A 인계 데모에서 B가 반드시 실제 추천 도구 목록까지 반환해야 하나요, 아니면 `NO_COMPATIBLE_TOOLS` 같은 정상 결과도 성공으로 볼까요? → A: B 추천 API가 실제로 실행되고 `RECOMMENDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS` 중 올바른 정상 결과를 반환하면 성공으로 본다.

## User Scenarios & Testing

### User Story 1 - 분석 요청을 실제 AI 처리로 완료한다 (Priority: P1)

사용자가 승인된 프로젝트의 분석을 요청하면 시스템은 처리 중 상태만 남기지 않고, 백그라운드 작업으로 분석을 끝까지 실행해 검증된 A DRAFT를 저장해야 한다.

**Why this priority**: 현재 가장 큰 미완성으로, 이 흐름이 없으면 A 분석 API가 성공적으로 호출되어도 실제 결과가 만들어지지 않는다.

**Independent Test**: 프론트엔드 없이 테스트용 프로젝트와 입력으로 분석을 요청하고, 처리 중 상태에서 성공 또는 실패의 terminal 상태로 이동하는지와 결과 저장 여부를 확인한다.

**Acceptance Scenarios**:

1. **Given** 승인된 프로젝트와 유효한 분석 입력이 있을 때, **When** 사용자가 분석을 요청하면, **Then** 분석 시도가 `PROCESSING`으로 생성되고 주기 Worker가 해당 시도를 처리한다.
2. **Given** Worker가 처리할 수 있는 분석 시도가 있을 때, **When** 분석이 성공하면, **Then** Core는 승인된 입력을 읽어 AI 분석을 호출하고 응답을 검증한 뒤 구조화된 A DRAFT를 저장하고 시도를 성공 상태로 종료한다.
3. **Given** AI 호출, 응답 검증, 기한, 또는 저장 중 하나가 실패할 때, **When** 시도가 종료되면, **Then** 자동 재시도 없이 성공한 A DRAFT를 만들지 않고 별도의 분석 실패 상태로 기록한다.
4. **Given** 같은 분석 시도를 여러 Worker가 동시에 발견할 때, **When** 하나의 Worker가 먼저 처리권을 확보하면, **Then** 나머지 Worker는 같은 시도를 중복 실행하지 않는다.

### User Story 2 - 분석이 끝난 뒤 원문을 제거하고 DRAFT만 남긴다 (Priority: P1)

사용자가 PDF, Markdown, 또는 직접 입력을 제출하면 시스템은 분석에 필요한 동안만 원문과 추출문을 사용하고, 분석이 성공하거나 실패한 terminal 상태에 도달하면 원문을 보관하지 않아야 한다. 성공한 경우에는 나중에 사용자가 확인할 수 있도록 구조화된 A DRAFT만 남긴다.

**Why this priority**: A DRAFT는 나중에 확인할 수 있어야 하지만, 원문을 장기 보관하지 않는다는 기존 데이터 수명 정책을 지켜야 한다.

**Independent Test**: 성공·실패·시간 초과·응답 형식 오류 각각에 대해 처리 전후 저장 데이터를 확인하고, 원문과 전체 추출문이 남지 않는지 검증한다.

**Acceptance Scenarios**:

1. **Given** 분석이 성공한 상태일 때, **When** terminal 처리가 완료되면, **Then** PDF·Markdown·직접 입력 원문과 전체 추출문은 제거되고 구조화된 A DRAFT와 필요한 감사 정보만 남는다.
2. **Given** 분석이 실패하거나 시간 초과한 상태일 때, **When** terminal 처리가 완료되면, **Then** 원문과 전체 추출문은 동일하게 제거되고 성공한 DRAFT로 오인될 결과는 남지 않는다.
3. **Given** 사용자가 분석 직후 확인하지 않은 상태일 때, **When** 나중에 DRAFT를 조회하면, **Then** 원문을 다시 읽지 않고도 DRAFT를 확인·수정할 수 있다.

### User Story 3 - 사용자가 확인한 A 결과로 별도 B 추천을 실행한다 (Priority: P1)

사용자는 A DRAFT를 확인하고 확정한 뒤 별도의 추천 요청을 실행한다. B 추천은 확정된 A 결과가 있을 때만 실행되며, 정상적인 추천 결과와 연동 실패는 서로 다른 결과로 표시된다.

**Why this priority**: A 분석과 B 추천을 분리해야 사용자가 분석 결과를 확인한 뒤 추천을 받는 현재 제품 흐름을 지킬 수 있다.

**Independent Test**: DRAFT 조회·확정 후 추천 API를 호출하고, 미확정 상태에서는 차단되며 확정 후에는 B 추천이 실행되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** 성공한 A DRAFT가 아직 확정되지 않았을 때, **When** 사용자가 B 추천을 요청하면, **Then** 시스템은 `PROFILE_NOT_CONFIRMED`로 거부하고 추천을 실행하지 않는다.
2. **Given** 사용자가 A DRAFT를 확인·수정하고 확정했을 때, **When** 별도의 B 추천을 요청하면, **Then** 확정된 Profile을 기반으로 B 추천을 실행한다.
3. **Given** B 분석에 필요한 정보가 부족할 때, **When** 추천이 완료되면, **Then** 정상 결과인 `NEEDS_INFORMATION`과 질문을 반환하고 연동 실패로 처리하지 않는다.
4. **Given** 충분한 정보가 있으나 조건에 맞는 도구가 없을 때, **When** 추천이 완료되면, **Then** 정상 결과인 `NO_COMPATIBLE_TOOLS`와 사유를 반환한다.
5. **Given** AI 호출, 계약 검증, 또는 저장이 실패할 때, **When** 추천이 종료되면, **Then** 정상 추천 상태로 위장하지 않고 별도의 실패 결과를 반환한다.

### User Story 4 - 운영자가 Docker 데모로 전체 백엔드 흐름을 검증한다 (Priority: P2)

운영자는 프론트엔드 없이 Docker 환경에서 A 분석, DRAFT 저장, 원문 제거, 사용자 확인, B 추천까지 한 번에 재현할 수 있어야 한다. 유료 AI 제공자를 사용할 때는 키를 환경변수로 주입하고 소스나 이미지에 포함하지 않는다.

**Why this priority**: 풀스택 A의 작업이 실제 배포 형태에서 검증되어야 풀스택 B 담당자에게 안정적으로 넘길 수 있다.

**Independent Test**: Docker Compose를 기동한 뒤 내부 API 또는 테스트 스크립트로 A→확인→B 순서를 실행하고, 각 단계의 상태와 저장 결과를 확인한다.

**Acceptance Scenarios**:

1. **Given** Docker Compose의 Core와 AI 서비스가 실행 중일 때, **When** Core가 분석·needs·rank 요청을 보내면, **Then** 세 요청이 AI 서비스의 내부 경로로 전달되고 공통 HMAC 인증을 통과한다.
2. **Given** Solar 제공자를 선택하고 실행 환경에 `UPSTAGE_API_KEY`가 설정되어 있을 때, **When** 작은 텍스트 입력으로 A 분석을 실행하면, **Then** AI 서비스만 키를 사용하고 키가 저장소·소스·일반 로그에 노출되지 않으며 실제 Solar 호출이 1회 성공한다.
3. **Given** 데모용 입력이 준비되어 있을 때, **When** 전체 시나리오를 실행하면, **Then** 프론트엔드 없이 A 분석부터 B 추천까지 성공 또는 명확한 실패 결과를 확인할 수 있다.

### Edge Cases

- 분석 요청은 생성되었지만 Worker가 처리권을 얻기 전에 재시작되는 경우.
- 동일한 분석 시도가 재시도되거나 두 Worker에 의해 동시에 발견되는 경우.
- AI 응답의 프로젝트 식별자·세대·계약 버전·필수 필드가 요청과 맞지 않는 경우.
- AI 응답이 `UNDETERMINED`와 질문을 포함하는 유효한 결과인 경우와, 필수 필드 누락·알 수 없는 Capability ID·형식 오류인 경우.
- AI 서비스가 늦게 응답하거나 내부 인증이 실패하는 경우.
- PostgreSQL의 실제 migration과 Repository가 식별자 타입 또는 상태 컬럼 이름을 다르게 사용하는 경우.
- V5 UUID 사용자로 로그인한 뒤 프로젝트 생성·조회·입력 교체·분석 시작 중 하나가 이전 숫자 ID 또는 `owner_id` SQL을 호출하는 경우.
- Solar 키가 없거나 잘못된 경우와 테스트용 결정적 제공자를 사용하는 경우.
- 원문 제거가 일부만 성공하거나 terminal 처리 중 재시작되는 경우.
- DRAFT는 존재하지만 사용자가 아직 확정하지 않은 상태에서 B 추천을 요청하는 경우.
- 일시적인 AI 오류가 발생해 사용자가 새 분석 요청을 시작하는 경우.

## Requirements

### Functional Requirements

- **FR-001**: 시스템은 분석 요청을 `PROCESSING`으로 등록한 뒤, 주기 Worker가 실제 비동기 처리 작업을 실행해야 한다.
- **FR-002**: Worker는 처리권을 확보한 분석 시도의 승인된 PlanningInput을 조회하고, AI 분석 요청에 필요한 최소 입력만 전달해야 한다.
- **FR-003**: 시스템은 AI 분석 응답의 요청 식별자, 프로젝트 식별자, 분석 세대, 계약 형식, 필수 필드, 처리 기한을 검증해야 한다.
- **FR-004**: 검증된 성공 응답은 기존 FullStack 계약에 맞는 구조화된 A DRAFT로 저장되어야 하며, 분석 시도는 성공 terminal 상태가 되어야 한다.
- **FR-005**: AI 호출, 응답 검증, 기한 초과, 내부 인증, 또는 저장 실패는 자동 재시도 없이 성공 결과와 구분되는 분석 실패 terminal 상태로 처리되어야 한다. 사용자는 새 분석 요청을 시작할 수 있어야 한다.
- **FR-006**: 어떤 terminal 상태에서도 PDF·Markdown·직접 입력 원문과 전체 추출문은 보관되지 않아야 한다. 정상 분석 실패 진단을 위해 원문을 장기 저장해서는 안 된다.
- **FR-007**: 성공한 A DRAFT는 사용자가 나중에 원문 재조회 없이 확인·수정·확정할 수 있도록 유지되어야 한다. DRAFT 확정은 DRAFT를 자동 삭제하지 않아야 한다.
- **FR-008**: B 추천은 사용자 확정으로 생성된 CONFIRMED Profile이 있을 때만 별도 실행되어야 하며, 미확정 상태는 `PROFILE_NOT_CONFIRMED`로 거부해야 한다.
- **FR-009**: `NEEDS_INFORMATION`과 `NO_COMPATIBLE_TOOLS`는 정상적인 B 결과로 처리하고, AI·계약·인증·기한·저장 실패는 별도의 실패 결과로 처리해야 한다.
- **FR-010**: 분석 시도 저장 구조와 Repository는 실제 PostgreSQL migration의 `UUID id`와 `status` 컬럼을 기준으로 식별자 타입, 반환 타입, 상태 전이 규칙을 동일하게 사용해야 한다.
- **FR-011**: PostgreSQL 환경에서 UUID 기반 분석 시도 생성·처리권 확보·성공·실패·시간 초과 조회가 SQL 컬럼명과 타입 불일치 없이 동작해야 한다.
- **FR-012**: Docker Compose는 Core의 분석 요청을 `http://ai-service:8001/internal/v1/analysis`, needs 요청을 `http://ai-service:8001/internal/v1/skills/needs`, rank 요청을 `http://ai-service:8001/internal/v1/skills/rank`로 연결해야 한다.
- **FR-013**: Docker 환경은 Core와 AI 서비스 사이의 HMAC 공유 설정을 유지해야 하며, HMAC 비밀값은 저장소에 하드코딩되지 않아야 한다.
- **FR-014**: Solar 제공자를 선택하는 경우 `AGENTFIT_AI_V2_PROVIDER=solar`와 `UPSTAGE_API_KEY`를 실행 환경에서 주입할 수 있어야 하며, 키가 코드·이미지·일반 로그에 포함되어서는 안 된다. Docker 데모는 작은 텍스트 입력으로 실제 Solar 분석을 1회 성공시켜야 한다.
- **FR-015**: 테스트 또는 데모 환경은 유료 키 없이도 결정적 제공자로 A→DRAFT→확인→B 흐름을 재현할 수 있어야 한다.
- **FR-016**: 구현 완료 여부는 프론트엔드 동작이 아니라 백엔드 내부 API 또는 테스트 시나리오로 A 분석→DRAFT 저장→원문 제거→사용자 확인→B 추천을 실제 실행해 판단해야 한다. B 추천은 `RECOMMENDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS` 중 요청·Catalog 상태에 맞는 정상 결과를 반환하면 통과이며, 실제 도구 목록은 필수 조건이 아니다.
- **FR-017**: 풀스택 A 작업은 Worker, PostgreSQL 정합성, Docker 전체 흐름 검증의 세 조건이 모두 충족되기 전에는 풀스택 B의 완료본으로 넘겨서는 안 된다.
- **FR-018**: Worker가 성공 결과를 저장하기 직전에는 PROJECT를 잠그고, analysis attempt가 시작할 때의 expected project version/basis, generation, deadline, 소유권 상태를 다시 검증해야 한다. 하나라도 달라지면 DRAFT나 `SUCCEEDED`를 만들지 않고 해당 attempt를 안전하게 실패 종료하며, 기존 CONFIRMED Profile과 성공한 추천 결과를 보존해야 한다.
- **FR-019**: V5 canonical schema를 사용하는 Core A의 인증 principal, Project 소유권·생성·조회, Project 생성 idempotency, PlanningInput 저장·현재 revision 조회·scrub, Audit 경로는 UUID 사용자·프로젝트 식별자만 전달해야 한다. 새 PostgreSQL 데모 경로에서 숫자 사용자 ID, `owner_id`, 이전 `PROJECT` 테이블을 조회하거나 대체 경로로 사용해서는 안 된다.

### Key Entities

- **Analysis Attempt**: UUID 식별자로 한 번의 분석 요청을 나타내며 `status`로 처리 중·성공·실패·시간 초과 상태, 요청 식별자, 프로젝트 식별자, 분석 세대, expected project version/basis를 가진다.
- **PlanningInput**: Worker가 분석 시점에 읽는 승인된 프로젝트 계획 입력이다. 원문 전체와 동일한 장기 저장 객체가 아니다.
- **A DRAFT**: AI 분석 결과를 검증·구조화한 사용자 확인 전의 프로필이다. 원문 없이도 조회·수정할 수 있다.
- **CONFIRMED Profile**: 사용자가 A DRAFT를 확인·확정한 뒤 B 추천의 입력으로 사용할 수 있는 프로필이다.
- **Transient Source Input**: PDF, Markdown, 직접 입력 원문과 전체 추출문이다. 분석 terminal 상태까지만 사용하고 이후 보관하지 않는다.
- **B Recommendation Outcome**: `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`, 또는 별도 연동 실패 중 하나로 표현되는 추천 결과다.
- **Demo Runtime Configuration**: Core·AI 서비스 주소, HMAC 공유 설정, 제공자 선택, Solar 키를 실행 환경에서 주입하는 설정이다.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 유효한 테스트 입력 1건으로 분석 요청부터 `PROCESSING` 생성, AI 호출, 검증, A DRAFT 저장, 성공 terminal 상태까지 프론트엔드와 수동 DB 수정 없이 완료된다.
- **SC-002**: 성공·실패·시간 초과를 포함한 모든 terminal 경로에서 PDF·Markdown·직접 입력 원문과 전체 추출문이 저장소에 남지 않는다.
- **SC-003**: 실제 PostgreSQL migration을 적용한 환경에서 UUID 기반 분석 시도 생성과 `status` 상태 전이가 식별자 타입·상태 컬럼 불일치 없이 성공한다.
- **SC-004**: Docker 환경에서 분석·needs·rank 세 내부 요청이 AI 서비스로 연결되고 HMAC 검증을 통과한다.
- **SC-005**: 사용자가 A DRAFT를 확정하기 전 B 추천은 차단되고, 확정 후 별도 요청으로 B 추천이 실행되어 `RECOMMENDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS` 중 하나의 정상 결과를 반환한다.
- **SC-006**: B 흐름에서 `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`, 연동 실패가 서로 다른 결과로 구분된다.
- **SC-007**: Solar 키가 없는 기본 데모 환경에서도 결정적 제공자로 전체 A→확인→B 시나리오를 재현할 수 있고, Solar 키를 주입한 환경에서는 동일한 계약으로 제공자만 교체되며 작은 텍스트 입력의 실제 Solar 분석이 1회 성공한다.
- **SC-008**: 풀스택 A의 인계 체크리스트인 Worker 연결, PostgreSQL 정합성, Docker 전체 실행 검증이 모두 증거와 함께 충족된다.
- **SC-009**: fresh V5+ migration PostgreSQL에서 UUID 로그인 사용자가 프로젝트 생성·조회·PlanningInput 저장/조회·분석 시작을 수행할 때 숫자 ID·이전 테이블·이전 컬럼 SQL 오류 없이 동작한다.

## Assumptions

- FullStack A는 프로젝트 소유권, 권한, PlanningInput 접근, 분석 시도 저장, DRAFT 저장, 원문 수명을 책임진다.
- AI 서비스는 분석·needs·rank 계산을 제공하지만 FullStack의 DB, 문서 수명, 사용자 권한, 사용자 확정 상태의 원천이 되지 않는다.
- A 분석과 B 추천은 별도 요청이며, 사용자는 A DRAFT를 확인·확정한 뒤 B 추천을 요청한다.
- 원문은 분석 terminal 상태까지 유지할 수 있지만, 성공 여부와 사용자 확정 여부에 관계없이 이후 보관하지 않는다. A DRAFT는 시간 제한 없이 유지할 수 있다.
- 실패 진단 원문 보관은 기본 비활성 정책을 따른다. 별도 허용이 있더라도 일반 데모 완료 조건에는 포함하지 않는다.
- 테스트·데모는 기본적으로 결정적 제공자를 사용하며, 풀스택 B 인계 전에는 `UPSTAGE_API_KEY`가 실행 환경에 주어진 Solar 실제 호출을 작은 텍스트 입력으로 1회 검증한다.
- 이번 데모의 PostgreSQL은 migration을 처음부터 적용한 새 데이터베이스다. 기존 운영 데이터의 `Long id`·`state` 변환은 이번 범위에 포함하지 않는다.
- B 담당자는 기존 FullStack B 추천 계약과 최종 추천 정책을 유지하며, rank 결과를 최종 추천에 반영할지는 별도 결정 사항이다.
- 풀스택 A 인계 데모의 B 성공 기준은 B API의 정상 실행과 상태 구분이다. 실제 추천 도구 목록을 만들기 위한 Catalog 준비는 B 담당자의 별도 책임이다.
- 지수 백오프, 재시도 횟수, 재시도 가능 오류 분류, 비용 예약 복구를 포함한 자동 재시도 정책은 이번 데모 범위 밖의 후속 작업이다.
- 데모 Worker는 일정 주기 조회 방식으로 시작한다. 향후 처리량·가용성 요구가 커지면 분석 시도의 상태와 처리권 규칙을 유지한 채 메시지 큐, 다중 Worker, 즉시 dispatch, dead-letter 처리, 지수 백오프 재시도를 별도 작업으로 추가할 수 있다.

## Handoff Criteria

풀스택 A가 풀스택 B 담당자에게 이 작업을 넘기는 조건은 다음 세 가지를 모두 충족하는 것이다.

- [ ] 분석 요청 후 Worker가 실제 AI 분석을 실행하고 A DRAFT를 저장한다.
- [ ] 실제 PostgreSQL에서 migration과 Repository의 식별자·상태 구조가 일치한다.
- [ ] Docker 환경에서 A 분석→DRAFT 저장→원문 제거→사용자 확인→B 추천을 실제 실행해, B의 정상 상태(`RECOMMENDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`) 중 하나를 확인한다.

세 조건 중 하나라도 미충족이면 “완료본”으로 인계하지 않고 A 작업으로 남긴다.
