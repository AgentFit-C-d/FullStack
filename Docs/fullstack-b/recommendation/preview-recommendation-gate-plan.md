# Preview 추천 소속 검증 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preview가 현재 프로젝트의 `RECOMMENDED` 결과에 속한 도구만 선택하도록 내부 경계를 연결한다.

**Architecture:** A가 인증·소유권을 확인하고 읽은 추천 기록과 현재 버전 basis를 B에 전달한다. B는 요청의 추천 ID·basis·선택 도구를 저장 기록과 대조한 다음 기존 Catalog·권한·템플릿 검증을 수행한다. HTTP·DB 계약은 여기서 정하지 않는다.

**Tech Stack:** Java 21 대상 Spring Boot 모듈, JUnit 5.

**Spec:** [설정 API 초안](../../../../Docs/api/03-configuration.draft.md)의 `RECOMMENDATION_NOT_READY`·`CONFIG_STALE` 조건 및 [B 다음 작업](../NEXT-STEPS.md)의 A 인계 경계.

## Global Constraints

- A가 제공하는 추천 기록과 현재 basis는 인증·소유권 검사를 마친 값이어야 한다. 브라우저의 주장으로 대체하지 않는다.
- `RECOMMENDED`가 아닌 상태, 현재 basis와 다른 상태, 추천 밖 도구는 Preview로 넘기지 않는다.
- 승인된 Catalog 해시·정확한 환경·권한·템플릿 검증은 기존 B 경계를 그대로 거친다.
- API/DB 저장 완료로 표시하지 않는다.

## Review Focus

- 추천 ID가 같은 프로젝트의 다른 기록을 가리키면 거부한다.
- Profile/환경/Catalog 버전이 바뀌면 저장 추천을 오래된 것으로 거부한다.
- 추천 목록에 없거나 중복된 선택 도구를 거부한다.
- 빈/중복/무효 추천 기록을 거부한다.
- 유효 추천이라도 Catalog 지원·권한 검사는 별도로 통과해야 한다.

---

### Task 1: 추천 상태 검증 경계

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/selection/StoredRecommendationState.java`, `RecommendationPreviewGate.java`; 같은 패키지의 `RecommendationPreviewGateTest.java`.

**Interfaces:** `RecommendationPreviewGate.requireEligible(StoredRecommendationState, PreviewBasis currentBasis, String requestedRecommendationId, PreviewBasis requestedBasis, List<String> selectedToolKeys)`.

- [x] 실패 테스트를 추가하고 실행한다.
- [x] 저장 추천의 상태·ID·basis·추천 도구 소속을 검사한다.
- [x] 집중 테스트를 통과시킨다.

### Task 2: Preview 조립 연결

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/catalog/CatalogPreviewAssembler.java`; `services/core-api/src/test/java/com/agentfit/coreapi/catalog/CatalogPreviewAssemblerTest.java`.

**Interfaces:** `assemble(Path, String approvedCatalogHash, StoredRecommendationState, PreviewBasis currentBasis, CatalogPreviewRequest)`.

- [x] 기존 조립 테스트를 새 신뢰 입력에 맞게 갱신하고 추천 밖 도구 거부 테스트를 추가한다.
- [x] 조립 진입점에서 추천 상태 검증을 Catalog 로드 전에 호출한다.
- [x] 전체 `mvn clean verify`와 `git diff --check`를 통과시킨다.
- [x] B09·B10의 부분 구현 범위만 문서화하고 한글 `feat/` 커밋을 만든다.
