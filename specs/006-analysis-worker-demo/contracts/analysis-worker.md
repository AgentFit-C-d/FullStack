# 분석 Worker 내부 계약

## 공개 API는 유지한다

사용자 요청은 기존 `POST /api/projects/{projectId}/analysis-attempts`를 사용한다. 응답은 202와
`PROCESSING` attempt이며, 이 요청은 AI 호출을 동기로 기다리지 않는다. 사용자 확인은 기존
`POST /api/projects/{projectId}/profile/confirm`, B 추천은 기존
`POST /api/projects/{projectId}/recommendations`를 사용한다.

## Worker claim 계약

| 단계 | 입력 | 보장 |
|---|---|---|
| claim | unclaimed `PROCESSING` attempt, 현재 시각 | 조건부 DB 갱신이 성공한 Worker 하나만 token을 얻는다. |
| 입력 조회 | claim token, `project_id`, `input_revision` | 해당 revision의 raw input을 메모리에서만 읽는다. 없으면 `FAILED/INPUT_UNAVAILABLE`로 끝낸다. |
| AI 호출 | `requestId`, generation, contractVersion, deadline, transient input | 기존 HMAC `POST /internal/v1/analysis`을 한 번 호출한다. storage URL·브라우저 cookie·DB 정보는 보내지 않는다. |
| 성공 terminal | claim token과 응답 | PROJECT를 잠그고 expected project version/basis·소유권·generation·deadline·status를 재검증한 뒤 DRAFT, `SUCCEEDED`, scrub, admission 해제, Audit을 함께 저장한다. |
| 실패 terminal | claim token과 최소 error code | `FAILED`, scrub, admission 해제, 최소 Audit을 함께 저장한다. |

## 실패와 중복 규칙

- HTTP 503을 포함한 AI 오류는 같은 attempt의 자동 재호출 사유가 아니다.
- claim lease가 만료된 attempt는 외부 AI 호출을 재개하지 않고 `WORKER_INTERRUPTED`로 실패 종료한다.
- terminal 갱신은 `id + status=PROCESSING + execution_token` 조건을 사용한다. 조건이 맞지 않으면
  늦은 Worker는 결과를 저장하거나 원문을 지우지 않는다.
- PROJECT가 삭제됐거나 expected project version/basis·generation이 달라진 늦은 응답은 DRAFT나 성공 상태를 만들지 않고 `STALE_ANALYSIS_RESULT`로 안전하게 종료한다. 기존 CONFIRMED Profile과 성공 추천은 보존한다.
- timeout 정리는 아직 terminal이 아닌 attempt만 `FAILED/TIMEOUT`으로 전환한다.
- Audit·오류 응답에는 raw input, 전체 추출문, Solar key, HMAC secret, 원시 AI 응답을 넣지 않는다.

## Core↔AI 계약

Worker는 기존 [AI OpenAPI 계약](../../../contracts/openapi/ai-service.yml)의
`POST /internal/v1/analysis`만 사용한다. needs/rank는 B의 별도 추천 요청에서 같은 HMAC client를
통해 호출한다. 이 기능은 경로·HMAC 서명 형식·응답 context 대조 규칙을 바꾸지 않는다.
