# 데이터 모델: 프로젝트 생성과 분석 시작 MVP

## 공통 규칙

- 모든 프로젝트 범위 조회·변경은 인증 주체와 `PROJECT.owner_id`를 서버에서 검증한다.
- 프로젝트 변경은 `PROJECT` 행을 잠근 뒤 `expectedVersion`을 확인하고, 성공 시 version을 1 증가시킨다.
- 원문·추출문·근거 인용문은 응답 DTO와 Audit metadata에 넣지 않는다.
- 시간은 PostgreSQL `TIMESTAMPTZ`, 문자 한도는 Java의 Unicode code point 기준으로 계산한다.

## 기존 엔터티 확장

### PROJECT

기존 `PROJECT`에 다음을 추가한다.

| 필드 | 타입/제약 | 설명 |
|---|---|---|
| `developer_role` | `VARCHAR(16)` NOT NULL, enum check | `FRONTEND`, `BACKEND`, `FULL_STACK`, `AI`, `DESIGNER` 중 하나 |
| `current_input_revision` | `BIGINT` NULL | 현재 기획 입력 메타데이터 버전 |

`name`, `version`, `analysis_generation`, 소유자와 시각 필드는 기존 의미를 유지한다.

### PROJECT_RUNTIME

| 필드 | 타입/제약 | 설명 |
|---|---|---|
| `project_id` | FK → `PROJECT`, NOT NULL | 소유 프로젝트 |
| `normalized_value` | `VARCHAR(200)` NOT NULL | trim 후 소문자로 정규화한 중복 판정값 |
| `display_value` | `VARCHAR(200)` NOT NULL | 최초 입력의 trim된 표시값 |
| `ordinal` | `SMALLINT` NOT NULL | 입력 순서, 0~29 |

- 기본 키는 `(project_id, normalized_value)`로 같은 프로젝트의 중복을 방지한다.
- 한 요청에서 줄바꿈·쉼표로 나누고 빈 값은 제거한다. 30개 초과 또는 항목 200 code point 초과는 전체 요청을 거부한다.

## 기획 입력

### PLANNING_INPUT

프로젝트 기획 입력의 버전별 메타데이터와 분석 기간에만 필요한 원문을 함께 둔다.

| 필드 | 타입/제약 | 설명 |
|---|---|---|
| `project_id` | FK → `PROJECT`, NOT NULL | 프로젝트 |
| `revision` | `BIGINT`, NOT NULL | 프로젝트별 1부터 증가하는 입력 버전 |
| `source_kind` | `VARCHAR(20)`, NOT NULL | `PDF`, `MARKDOWN_FILE`, `TEXT` |
| `content_type` | `VARCHAR(100)`, NOT NULL | PDF/Markdown 파일 MIME 또는 `text/plain` |
| `original_file_name` | `VARCHAR(255)` NULL | 파일 입력에서만 보존하는 이름 |
| `byte_size` | `BIGINT`, NOT NULL | 입력 바이트 수, 최대 10,485,760 |
| `page_count` | `INTEGER` NULL | PDF에서만 1~100 |
| `text_code_points` | `INTEGER`, NOT NULL | 추출/직접 입력의 문자 수, 최대 100,000 |
| `raw_content` | `BYTEA` NULL | 파일 바이트 또는 UTF-8 직접 입력. 종료 시 `NULL` |
| `extracted_text` | `TEXT` NULL | PDF 검증·분석용 추출문. 종료 시 `NULL` |
| `raw_deleted_at` | `TIMESTAMPTZ` NULL | 원문 삭제 완료 시각 |
| `created_at` | `TIMESTAMPTZ`, NOT NULL | 입력 접수 시각 |

- 기본 키: `(project_id, revision)`.
- 프로젝트 생성 또는 입력 교체 시 새 revision을 만든다. 이전 revision의 메타데이터는 보존한다.
- Attempt 종료 전에는 현재 revision의 원문만 존재할 수 있다.
- 종료 처리의 원자적 결과: Attempt terminal state 저장, `raw_content`/`extracted_text` 제거, `raw_deleted_at` 기록, admission 슬롯 회수, 최소 Audit 기록.

## 분석 상태

### ANALYSIS_ATTEMPT (기존 V3 확장)

| 필드 | 타입/제약 | 설명 |
|---|---|---|
| `id` | 기존 BIGSERIAL PK | Attempt 식별자 |
| `project_id` | FK → `PROJECT` | 대상 프로젝트 |
| `owner_id` | FK → `USER`, NOT NULL | 사용자 동시성 인덱스용 비정규화 소유자 |
| `input_revision` | BIGINT, NOT NULL | 참조한 `PLANNING_INPUT` revision |
| `request_id` | 기존 UNIQUE | 내부 추적/향후 AI 상관관계용. 생성 멱등성 키가 아님 |
| `generation` | 기존 프로젝트별 증가 | 분석 generation |
| `state` | `PROCESSING`, `SUCCEEDED`, `FAILED` | 현재 상태 |
| `error_code` | `VARCHAR(50)` NULL | 만료 시 `TIMEOUT`, 그 밖의 안전한 실패 코드 |
| `deadline_at` | `TIMESTAMPTZ` | 시작 + 600초 |
| `started_at`, `completed_at` | `TIMESTAMPTZ` | 기존 시각 필드 |

제약과 인덱스:

- `UNIQUE (project_id) WHERE state = 'PROCESSING'`: 같은 프로젝트의 활성 분석 1건.
- `UNIQUE (owner_id) WHERE state = 'PROCESSING'`: 같은 사용자의 활성 분석 1건.
- 기존 `(project_id, generation)` unique는 유지한다.

상태 전이:

```text
없음 ── 분석 시작 ──> PROCESSING
PROCESSING ── 실제 분석 성공(후속 기능) ──> SUCCEEDED
PROCESSING ── 600초 만료(MVP) ──> FAILED(TIMEOUT)
PROCESSING ── 취소/안전한 실패(후속 기능) ──> FAILED
```

terminal state 전환은 `WHERE state = 'PROCESSING'` 조건을 사용해 중복 정리와 슬롯 중복 반환을 방지한다. `PROCESSING` Attempt가 하나라도 있는 프로젝트는 삭제하지 않아 활성 Attempt의 FK cascade가 admission 슬롯을 누수시키지 않게 한다.

### ANALYSIS_ADMISSION

서버 전체 활성 Attempt 수를 직렬화하는 단일 행이다.

| 필드 | 타입/제약 | 설명 |
|---|---|---|
| `id` | 고정값 `1`, PK | singleton 행 |
| `active_count` | `SMALLINT`, 0~2 | 현재 `PROCESSING` Attempt 수 |
| `updated_at` | `TIMESTAMPTZ` | 마지막 변화 시각 |

- 분석 시작: 프로젝트 행 잠금 후 admission 행을 `FOR UPDATE`로 잠그고 `active_count < 2`를 확인한다.
- terminal 처리: 같은 순서로 잠근 뒤 조건부 종료에 성공했을 때만 1 감소시킨다.
- timeout scanner는 최대 10초 간격으로 실행한다.

## 생성 멱등성

### PROJECT_CREATION_IDEMPOTENCY

| 필드 | 타입/제약 | 설명 |
|---|---|---|
| `owner_id` | FK → `USER`, NOT NULL | 요청 사용자 |
| `operation` | 고정값 `PROJECT_CREATE` | 향후 다른 기능 키와 분리 |
| `idempotency_key` | `VARCHAR(128)`, NOT NULL | `Idempotency-Key` 헤더 값 |
| `request_fingerprint` | `CHAR(64)`, NOT NULL | 동일 키의 다른 요청 감지용 SHA-256 요약값 |
| `project_id` | FK → `PROJECT`, NULL → NOT NULL | 완료된 생성 결과 |
| `created_at` | `TIMESTAMPTZ`, NOT NULL | 키 예약 시각 |

- 유니크 키: `(owner_id, operation, idempotency_key)`.
- 동일 fingerprint와 이미 완료된 키는 해당 `project_id`의 기존 응답을 반환한다.
- 다른 fingerprint로 같은 키를 사용하면 새 프로젝트를 만들지 않고 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
- 생성 트랜잭션이 실패하면 키 예약도 함께 롤백한다.

## 응답용 조합

`GET /api/projects/{projectId}`는 다음을 하나의 소유자 전용 응답으로 조합한다.

1. `PROJECT`의 이름·role·version·시각
2. `PROJECT_RUNTIME`의 `ordinal` 순서 목록
3. 현재 `PLANNING_INPUT`의 안전한 메타데이터(원문 제외)
4. 최신 `ANALYSIS_ATTEMPT`의 상태·기한·안전한 오류 코드
5. 결과 생성 기능이 추가된 뒤의 최신 결과(이번 MVP에서는 `null`)

타인·삭제 프로젝트는 동일한 `PROJECT_NOT_FOUND` 응답으로 감춘다.
