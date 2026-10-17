# 데이터 모델: Worker 실행과 terminal 처리

## 소유권과 보관

| 데이터 | 소유자 | 보관 규칙 |
|---|---|---|
| PlanningInput 원문·추출문 | Core A | 분석 terminal 처리까지. 성공·실패·timeout·중단에서 scrub한다. |
| AnalysisAttempt | Core A | UUID, 요청·세대·상태·claim·최소 오류 코드만 남긴다. 원문은 넣지 않는다. |
| A DRAFT | Core A | 검증된 구조화 결과만 저장한다. 확정 뒤에도 DRAFT는 유지한다. |
| CONFIRMED Profile·확인 이벤트 | Core A | 사용자 확인 transaction으로 생성하며 B의 입력이다. |
| B Recommendation | Core B | CONFIRMED Profile 기준의 정상 상태 또는 별도 실패를 표현한다. |
| Solar key·HMAC secret | Docker 실행 환경 | `.env.docker`에서만 공급하고 DB·Audit·일반 로그에 저장하지 않는다. |

## AnalysisAttempt

### 기준 필드

| 필드 | 의미와 규칙 |
|---|---|
| `id` | Core가 생성하는 UUID. 외부 API에는 문자열 UUID로만 노출한다. |
| `project_id` | UUID 프로젝트 식별자. terminal 저장 전 프로젝트·generation을 재검증한다. |
| `request_id` | HMAC 추적과 AI 응답 대조용 고유 값이며 단독 멱등성 보장은 아니다. |
| `generation` | 프로젝트별 분석 세대. 늦은 응답은 현재 세대와 다르면 저장하지 않는다. |
| `expected_project_version` / `expected_basis` | 분석 요청 시점의 PROJECT version과 basis. 성공 terminal 직전에 PROJECT를 잠근 뒤 현재 값과 대조한다. |
| `status` | `PROCESSING`, `SUCCEEDED`, `FAILED` 중 하나. timeout도 `FAILED` + `TIMEOUT`이다. |
| `deadline_at` / `finished_at` | AI 호출 허용 기한과 terminal 시각이다. |
| `owner_id` / `input_revision` | `owner_id`는 V5 `USER.id`를 참조하는 UUID이고, `input_revision`은 정확히 scrub할 PlanningInput revision이다. |
| `execution_token` / `claimed_at` / `lease_expires_at` | Worker가 처리권을 가진지 검증하는 내부 claim 정보다. 외부 DTO·Audit에 원문과 함께 넣지 않는다. |
| `result_draft_id` / `result_draft_version` | 성공 결과로 저장한 DRAFT의 식별자·버전이다. |
| `error_code` | 원문·원시 AI 응답이 아닌 정규화된 최소 실패 코드다. |

### 상태 전이

```text
새 분석 요청
  → PROCESSING (unclaimed)
  → PROCESSING (claimed, token 보유)
  ├─ 성공: SUCCEEDED + DRAFT + scrub + admission 해제 + Audit
  ├─ AI/계약/저장 오류: FAILED + 최소 오류 코드 + scrub + admission 해제 + Audit
  ├─ project version/basis·generation 불일치 또는 삭제: FAILED(STALE_ANALYSIS_RESULT) + scrub + admission 해제 + 최소 Audit
  ├─ deadline 경과: FAILED(TIMEOUT) + scrub + admission 해제 + Audit
  └─ claim 만료: FAILED(WORKER_INTERRUPTED) + scrub + admission 해제 + Audit
```

`SUCCEEDED`와 `FAILED`는 terminal이다. terminal 상태에서 같은 attempt를 다시 AI에 보내지
않는다. 늦은 Worker 응답은 기존 CONFIRMED Profile·성공 추천을 덮어쓰지 않는다. 사용자의 새 분석 요청은 새 UUID attempt와 새 generation을 만든다.

## PlanningInput

`project_id(UUID) + revision`으로 한 번의 입력 버전을 식별한다. Worker는 claim 뒤 현재 attempt가
가리키는 revision만 메모리로 읽어 AI 입력을 만든다. 성공·실패·timeout·중단의 조건부 terminal
transaction에서 해당 revision의 `raw_content`, `extracted_text`, `citations`을 모두 NULL 처리한다.
입력 메타데이터만 필요하면 별도 summary 메타데이터로 남길 수 있지만, 재조회 위치나 원문은
남기지 않는다.

## A DRAFT와 CONFIRMED Profile

Worker는 검증된 AI 응답의 허용 planning field만 A DRAFT에 기록한다. 사용자가 확인·수정·확정하면
기존 `ProfileConfirmationService`가 CONFIRMED Profile·확인 이벤트·Audit을 한 transaction으로
만든다. 확정은 DRAFT를 삭제하지 않는다.

## B Recommendation Outcome

| 결과 | A 인계 데모 의미 |
|---|---|
| `RECOMMENDED` | B Catalog가 적합한 검증 조합을 찾았다. |
| `NEEDS_INFORMATION` | 정상 AI 판단 결과이며 질문이 남았다. |
| `NO_COMPATIBLE_TOOLS` | 정보는 충분하지만 B Catalog에 맞는 조합이 없다. 정상 결과다. |
| 연동 실패 | AI·계약·저장·권한 실패다. 정상 B 상태로 바꾸거나 기존 성공 결과를 덮지 않는다. |
