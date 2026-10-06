# 추천·도구 선택·권한

## 이 폴더의 설계 기록

- [설정 선택 검증](configuration-selection-plan.md): 추천 후 선택한 도구의 정확한 환경 지원, 의존성·충돌·포함 컴포넌트, 권한 매핑·정책을 재검사한다.
- [대상별 다중 도구 조합](../catalog/targeted-combinations-plan.md): 함께 설치할 조합의 검증 조건은 Catalog 설계에 있다.

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/`: `CapabilityKey`(고정 9개 Capability), `EnvironmentTarget`, `RecommendationEngine`, `RecommendationInput`, `RecommendationDecision`(4가지 추천 결과).
- `services/core-api/src/main/java/com/agentfit/coreapi/recommendation/selection/`: `ConfigurationSelectionValidator`, `PermissionPolicyGate`, `PermissionMapping`, `PermissionPolicy`, `PermissionSelection`이 도구·권한 선택을 재검증한다.
- 테스트는 `services/core-api/src/test/java/com/agentfit/coreapi/recommendation/`와 그 아래 `selection/`에 각각 있다.

이 영역은 현재 순수 도메인 로직이다. A의 프로젝트·Developer·Environment 현재 스냅샷 및 AI Capability 응답과 연결되지 않았고, 추천 DB/API도 없다. 연결 순서는 [다음 작업](../NEXT-STEPS.md)의 1·2·4번을 따른다.
