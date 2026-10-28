# 추천·도구 선택·권한

## 현재 확인할 문서

- [추천 워크플로](catalog-recommendation-workflow.md): A의 스냅샷, AI Capability 후보, 승인 Catalog를 B 판정에 연결하는 내부 경계.
- [최신 추천 조회](latest-read-model.md): 저장된 결과가 현재 버전 기준으로 유효한지 판정한다.
- [선택 후보 계약 제안](optional-candidates-contract-proposal.md): B의 `optionalItems` 구현과 AI·Frontend에 남은 의미·화면 계약.
- [A·AI 계약 제안](../integration/ab-ai-contract-proposal.md): 실제 연동 전에 확정할 입력·응답 필드.

AI 후보·도구 선택·Preview 소속 검증의 단계별 구현 체크리스트는 [계획 기록](../archive/README.md)에 있다.

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/`: `CapabilityKey`(고정 9개 Capability), `EnvironmentTarget`, `RecommendationEngine`, `RecommendationInput`, `RecommendationDecision`(4가지 추천 결과).
- `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/selection/`: `ConfigurationSelectionValidator`, `PermissionPolicyGate`, `RecommendationPreviewGate`와 관련 모델이 도구·권한·추천 소속을 재검증한다.
- `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/ai/`: `AiCapabilityIntake`가 AI 후보를 신뢰된 필드 범위로 검증한다.
- 테스트는 `services/core-api/src/test/java/com/agentfit/coreapi/recommendation/`와 그 아래 `ai/`, `selection/`에 있다.

추천 B1–B4 메타데이터 조립·PostgreSQL 기록·최신 조회는 `recommendation/persistence/`에 있다. A의 확인된 Profile·Developer·Environment 스냅샷과 AI Capability HTTP 응답은 인증된 추천 API에 연결돼 있다. 실제 `OPTIONAL` 제안은 AI가 아직 생성하지 않으며, 프론트도 `optionalItems` 선택 UI를 표시하지 않는다. 실제 승인 Claude Code Catalog와 PostgreSQL 실서버에서 선택 후보 경로를 검증해야 한다. 연결 상태와 남은 범위는 [현재 상태](../STATUS.md)를 따른다.
