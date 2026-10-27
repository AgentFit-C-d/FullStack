# 별도 AI 저장소와 Core·B 계약 정렬 — 2026-10-27

## 비교 기준과 현재 결론

- FullStack `feat/fullstack-b-finalization`의 `59c03e0`와 별도 `D:/capstone/AgentFit_AI_v2`의 `2164a43`을 비교했다.
- 현재 로컬 Compose의 Core는 **FullStack 안의 FastAPI**를 호출한다. 이 경로는 분석 → CONFIRMED Profile → B의 9종 Capability → Catalog 판정까지 동작한다. 별도 AI 저장소를 Core의 AI 주소로 바꿔 끼우는 것은 아직 동작하지 않는다.
- B는 AI의 `needs` 검색어를 9종 Capability의 `REQUIRED` 판정으로 자동 변환하지 않는다. 그렇게 하면 필수 기능·선택 기능·미정의 의미와 근거가 달라진다.

## 실제 HTTP 계약 차이

| 경계 | 현재 FullStack Core가 보내고 받는 값 | 별도 AI 저장소의 현재 계약 | 필요한 정렬 |
| --- | --- | --- | --- |
| 분석 | HMAC `POST /internal/v1/analysis`; `{requestId,generation,contractVersion,input}`와 같은 문맥의 `{...,result}` | Bearer `POST /internal/v1/analyze`; `attemptId`, `leaseId`, `inputRef`, `provider`, `deadline`, `documentId`, `inputKind` 등을 요구 | A·AI가 인증, 원문 전달·수명, 예산 예약, 문맥·응답을 하나의 어댑터 계약으로 합의 |
| Capability | HMAC `POST /internal/v1/skills/needs`; `input.facts`의 ID는 `profile.item.features.0` 같은 A 확정 필드 경로. 응답 `result.claims` 9종과 `result.questions`가 필요 | Bearer `POST /internal/v1/skills/needs`; `facts` ID는 `features:0` 형태, `deadline`과 `budget.targetId/leaseId`가 필요. 결과는 검색용 `needs[]`뿐 | AI가 9종 `claims`와 질문을 Core 계약으로 제공하거나 A·AI가 검증 가능한 명시적 변환 계약을 확정 |
| 추천 판정 | B는 확정 입력·AI Capability·검증된 Catalog·환경을 대조해 네 상태와 최소/부분 구성을 저장 | AI의 `/skills/rank`는 전달 후보의 적합도 점수와 설명을 반환 | AI 점수는 후보 조사 보조 자료로만 사용. OS·Client·권한·의존·조합 판정을 대체하지 않음 |
| 실행 환경 | FullStack AI Dockerfile은 Python 3.11 | 별도 AI 저장소는 Python 3.12 범위 | 패키징·런타임을 통합 배포 전에 합의 |

별도 AI의 `skills.json`은 `facts` 최대 96개, 값 최대 512자, ID 정규식 `^[a-z_]+:[0-9]{1,3}$`를 요구한다. B의 현재 입력은 A의 확정 항목·담당 업무 경로를 근거 ID로 쓰고, FullStack AI는 이를 그대로 `sourceFields`에 돌려준다. ID를 바꾼다면 양방향 매핑과 근거 검증을 함께 정의해야 한다.

### 현재 Core가 요구하는 needs 교환 예시

아래는 설명용 최소 예시다. 실제 요청은 Core가 본문과 경로·시각에 대해 HMAC 헤더를 생성한다. 응답의 `claims`는 **고정 9종 전부** 필요하다.

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "generation": 1,
  "contractVersion": "recommendation-capability-v1",
  "input": {
    "facts": [
      {"id": "profile.item.features.0", "field": "features", "value": "Write tests"}
    ]
  }
}
```

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "generation": 1,
  "contractVersion": "recommendation-capability-v1",
  "result": {
    "claims": [
      {"capabilityKey":"cap_document_reference","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]},
      {"capabilityKey":"cap_repository_navigation","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]},
      {"capabilityKey":"cap_code_review","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]},
      {"capabilityKey":"cap_test_authoring","need":"REQUIRED","reason":"테스트 작성 작업이 명시됨","sourceFields":["profile.item.features.0"]},
      {"capabilityKey":"cap_test_execution","need":"NOT_REQUESTED","reason":"실행은 명시되지 않음","sourceFields":[]},
      {"capabilityKey":"cap_browser_verification","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]},
      {"capabilityKey":"cap_api_verification","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]},
      {"capabilityKey":"cap_database_schema_inspection","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]},
      {"capabilityKey":"cap_ai_evaluation","need":"NOT_REQUESTED","reason":"확정 입력에 없음","sourceFields":[]}
    ],
    "questions": []
  }
}
```

이 예시는 **계약 형식**을 보여주며 `Write tests`의 실제 Capability 판단을 확정하지 않는다. 해당 표현에 테스트 실행이 포함되는지는 AI 담당자와 별도 합의가 필요하다.

## B에서 정렬한 부분

- `AiCapabilityAnalyzerAdapter.facts()`가 허용된 확정 기획 항목과 담당 업무만 보내는 기존 경계를 유지하고, Map 순서와 관계없이 근거 ID 순서를 고정한다.
- 기존 `AiCapabilityIntake`의 9개 고정 키, 네 가지 필요도, 허용된 근거 경로 검증을 유지한다. `claims`나 `questions`가 없는 응답은 계약 실패이며 추천 성공으로 저장하지 않는다.
- 별도 AI처럼 `needs[]`만 돌아온 경우 `AI_NEEDS_ONLY_RESPONSE` 계약 오류로 구분한다. 검색 결과를 Capability 판정으로 오인하지 않는다.
- skills.sh 수집 결과는 계속 **미검증 후보**다. 팀 승인 해시가 있는 Catalog만 B 추천·Preview·ZIP에 사용한다.

## A·AI 담당자에게 필요한 결정과 구현

1. **분석 어댑터:** 현재 Core HMAC·요청 문맥을 보존할지, A가 별도 내부 게이트웨이를 만들지 정한다. URL만 바꾸거나 HMAC을 브라우저/사용자 Bearer로 대체하지 않는다.
2. **원문과 예산:** Core가 보유한 원문 전달/삭제 방식과 별도 AI의 `inputRef`·`leaseId`·예산 예약을 연결한다. 성공 후 DB 저장 실패, 시간초과, 재시도에서 원문 수명과 비용을 함께 다룬다.
3. **Capability 응답:** 고정 9개 키 각각에 `need: REQUIRED | OPTIONAL | UNDETERMINED | NOT_REQUESTED`, `reason`, A 허용 필드를 가리키는 `sourceFields`를 제공한다. 질문은 `field`와 `reason`으로 제공한다. `needs[]` 검색어만으로 이 판정을 추정하지 않는다.
4. **정보 보완:** 저장된 질문 답변을 확정 기획/환경으로 갱신할지, 별도 승인된 facts로 보낼지 정한다. 현재 답변 저장 API는 기록만 남기며 재추천 입력은 바꾸지 않는다.

## 연결 수용 기준

- A의 실제 CONFIRMED Profile에서 HMAC 문맥·원문 수명·예산을 지켜 별도 AI 분석이 성공하고, 실패/시간초과도 구분된다.
- 같은 확정 입력에서 AI가 근거가 있는 9종 Capability와 질문을 반환하며 B가 이를 검증해 PostgreSQL에 저장한다. `OPTIONAL`은 필수와 구별되고 실제 응답으로 확인된다.
- 팀이 승인한 실물 Catalog에 한해 B가 `RECOMMENDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`, `NO_ADDITIONS_NEEDED`를 구분하고 Preview→승인→ZIP까지 일치시킨다.
- 위 계약이 정렬되기 전에는 별도 AI 저장소를 Compose의 활성 AI 서비스로 표시하거나 실물 Catalog 미승인 후보를 추천 대상으로 올리지 않는다.

## 근거 파일

- Core: `services/core-api/src/main/java/com/agentfit/coreapi/domain/analysis/client/HttpAiAnalysisClient.java`, `integration/ab/AiCapabilityAnalyzerAdapter.java`, `recommendation/ai/AiCapabilityIntake.java`
- 현재 FastAPI: `services/ai-service/app/main.py`, `ai_v2/needs.py`
- 별도 AI: `D:/capstone/AgentFit_AI_v2/apps/ai/agentfit_ai/main.py`, `skill_discovery.py`, `packages/contracts/schema/skills.json`, `packages/contracts/schema/internal.json`
