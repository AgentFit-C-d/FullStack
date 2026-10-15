# Core API 계약: 프로젝트 생성과 분석 시작 MVP

기준 OpenAPI 파일은 `contracts/openapi/core-api.yml`이다. 이 문서는 003 구현에서 그 파일에 반영할 계약 결정을 설명한다. 이번 변경은 기존 JSON 프로젝트 생성 endpoint를 multipart 생성으로 바꾸는 의도된 breaking change이므로 OpenAPI 버전은 `0.2.0`으로 올린다.

모든 쓰기 요청은 기존 CSRF 규칙을 따른다. `AGENTFIT_SESSION` 쿠키로 인증하며, 응답에는 `ownerId`, 원문 바이트, 직접 입력 텍스트, PDF 추출문, 근거 인용문을 포함하지 않는다.

## 공통 오류

모든 오류는 기존 `ErrorResponse` 봉투를 사용한다.

| HTTP | error.code | 의미 |
|---|---|---|
| 401 | `UNAUTHENTICATED` | 로그인 세션 없음 |
| 403 | `CSRF_INVALID` | 쓰기 요청 CSRF 토큰 없음/불일치 |
| 404 | `PROJECT_NOT_FOUND` | 프로젝트가 없거나 요청자의 소유가 아님 |
| 409 | `VERSION_CONFLICT` | `expectedVersion`이 현재 프로젝트 version과 다름 |
| 409 | `ANALYSIS_ALREADY_PROCESSING` | 같은 프로젝트에 활성 Attempt 존재 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 동일 생성 키에 다른 요청 본문 사용 |
| 409 | `ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN` | `PROCESSING` Attempt가 있는 프로젝트 삭제 요청 |
| 422 | `INVALID_INPUT` | 입력 형식·한도·상호 배타 입력 검증 실패 |
| 429 | `ANALYSIS_USER_LIMIT_REACHED` | 사용자의 활성 분석 1건 제한 |
| 429 | `ANALYSIS_SERVER_LIMIT_REACHED` | 서버 전체 활성 분석 2건 제한 |

`ANALYSIS_ALREADY_PROCESSING`의 사용자 메시지는 반드시 다음과 같다.

> 분석이 이미 진행 중입니다. 완료될 때까지 기다려 주세요.

## 프로젝트 생성

### `POST /api/projects`

프로젝트와 첫 기획 입력을 같은 트랜잭션으로 생성한다.

필수 헤더:

```text
Idempotency-Key: 1~128자 비공백 문자열
X-XSRF-TOKEN: <GET /api/auth/csrf 로 받은 값>
```

`Content-Type: multipart/form-data`이며 다음 파트를 사용한다.

| 파트 | 형식 | 필수 | 규칙 |
|---|---|---|---|
| `metadata` | `application/json` | 예 | `name`, `role`, `runtimeInput` |
| `document` | PDF 또는 Markdown 파일 | 조건부 | `inputText`와 정확히 하나 |
| `inputText` | UTF-8 `text/plain` | 조건부 | `document`와 정확히 하나 |

`metadata` 예:

```json
{
  "name": "AgentFit API",
  "role": "BACKEND",
  "runtimeInput": "Java 21\nSpring Boot, PostgreSQL"
}
```

검증:

- 이름: trim 뒤 1~100 Unicode code points
- role: `FRONTEND`, `BACKEND`, `FULL_STACK`, `AI`, `DESIGNER`
- 런타임: 줄바꿈/쉼표 분리, 빈 값 제거, 대소문자 무시 중복 제거, 최대 30개, 항목당 최대 200 code points
- 파일: PDF 또는 Markdown, 최대 10 MiB(10,485,760 bytes)
- PDF: 최대 100쪽, 추출문 최대 100,000 code points
- 직접 입력: `text/plain`, 최대 100,000 code points

응답:

- `201 Created`: 새 프로젝트 상세 응답
- 동일 키·동일 요청: 원래 생성 결과를 `201 Created`로 다시 반환
- 동일 키·다른 요청: `409 IDEMPOTENCY_KEY_REUSED`

## 프로젝트 정보 조회와 수정

### `GET /api/projects/{projectId}`

소유자만 조회한다. 응답은 프로젝트 이름, role, normalized runtime 목록, 현재 입력의 안전한 메타데이터, 최신 Attempt, `latestResult: null`을 포함한다. Analysis가 종료된 경우 원문과 추출문은 어떤 필드로도 제공하지 않는다.

### `PATCH /api/projects/{projectId}`

프로젝트 정보만 수정한다. 원문 교체는 별도 입력 endpoint를 사용한다.

```json
{
  "expectedVersion": 3,
  "name": "AgentFit Core API",
  "role": "BACKEND",
  "runtimeInput": "Java 21, Spring Boot, PostgreSQL"
}
```

- 성공 시 version을 증가시켜 최신 상세 응답을 `200 OK`로 반환한다.
- 오래된 `expectedVersion`은 `409 VERSION_CONFLICT`다.

### `PUT /api/projects/{projectId}/planning-input`

생성 API와 동일한 multipart 입력 규칙에 `expectedVersion`을 `metadata`에 추가한다. 새 기획 입력 revision을 만들고, 이후 분석은 이 새 revision을 사용한다. 활성 Attempt가 있으면 `409 ANALYSIS_ALREADY_PROCESSING`으로 거부한다.

### `DELETE /api/projects/{projectId}`

기존 002 삭제 계약을 유지한다. 단, `PROCESSING` Attempt가 있으면 `409 ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN`으로 거부한다. 이 경우 Attempt·입력 원문·`ANALYSIS_ADMISSION.active_count`는 변경하지 않는다. terminal 상태 뒤에만 기존 cascade 삭제를 허용한다.

## 분석 시작과 상태

### `POST /api/projects/{projectId}/analysis-attempts`

```json
{
  "expectedVersion": 3
}
```

- 서버는 프로젝트 소유권과 현재 입력 존재 여부를 검증한다.
- 성공 시 프로젝트와 admission을 잠그고 새 Attempt를 `PROCESSING`으로 만들며, `deadlineAt`은 시작 600초 뒤다.
- 성공 응답은 `202 Accepted`와 Attempt 메타데이터를 반환한다.
- 같은 프로젝트의 활성 Attempt: `409 ANALYSIS_ALREADY_PROCESSING`.
- 다른 프로젝트의 사용자 제한 또는 서버 용량 제한: 각각 `429` 전용 오류 코드.
- API는 이번 MVP에서 AI 호출·성공 결과 생성을 수행하지 않는다.

### 만료 정리

외부 공개 endpoint가 아니다. 스케줄러는 최대 10초 간격으로 기한이 지난 `PROCESSING` Attempt를 `FAILED` + `TIMEOUT`으로 바꾸고 다음을 같은 정리 흐름에서 수행한다. 이번 MVP의 terminal 전이는 timeout뿐이며, 후속 성공·실패·취소 경로도 이 내부 정리 흐름을 재사용해야 한다.

1. `PLANNING_INPUT.raw_content`, `extracted_text` 삭제
2. `raw_deleted_at` 기록
3. `ANALYSIS_ADMISSION.active_count` 1 감소
4. 원문을 포함하지 않는 최소 Audit 기록

이미 종료된 Attempt는 재변경하거나 슬롯을 다시 줄이지 않는다.
