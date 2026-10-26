# FSB-04 선택 도구 구현 계획

## 기준과 설계

- `OPTIONAL`은 AI가 승인된 입력 경로를 근거로 명시한 경우에만 선택 후보 탐색에 사용한다. `NOT_REQUESTED`와 `UNDETERMINED`는 후보로 승격하지 않는다. 현재 AI 구현은 `OPTIONAL`을 생성하지 않으므로 기본 시연 결과는 바뀌지 않는다.
- 먼저 필수 Capability를 만족하는 최소 기본 구성을 판정한다. 기본 결과가 `RECOMMENDED`일 때만 선택 후보를 탐색한다. `NO_ADDITIONS_NEEDED`의 빈 항목 불변 조건은 유지한다.
- 선택 후보는 정확한 OS·Client·버전 지원, 의존성·충돌·중복, 기본 구성과의 **전체 조합 검증**, 고정 출력의 미리보기 크기 제한을 모두 통과해야 한다. 후보 하나씩 검증하며 후보끼리의 임의 조합은 추정하지 않는다.
- 공개 결과와 DB 항목의 `required`로 기본 도구와 선택 도구를 구분한다. 선택 도구는 사용자가 명시적으로 선택하기 전에는 권한 승인·설치·Preview에 포함하지 않는다. Preview는 선택된 전체 조합을 다시 검증한다.
- 팀의 AI `OPTIONAL` 생성 정책과 실제 승인 Catalog가 준비되기 전까지 이 기능은 B 경계의 구현으로만 취급한다. FSB-04 제품 완료로 세지 않는다.

## 구현 순서

1. `OptionalCatalogCandidateSelector`에 순수 선택 후보 판정을 추가한다. 검증된 기본 구성과 후보의 정확한 전체 조합만 허용하고, `OPTIONAL` 근거가 없는 도구는 제외한다. 실패 테스트부터 작성한다.
2. `CatalogRecommendationWorkflow`에서 필수 판정 뒤 선택 후보를 합치고, 응답 항목에 `required`와 충족한 선택 Capability를 표시한다. 고정 출력 예산을 검사한다.
3. 추천 항목의 `required`를 PostgreSQL에 저장하고 최신 조회·미리보기·승인·ZIP까지 같은 의미로 전달한다. 저장 결과의 필수 항목이 사라지거나 선택 후보가 필수로 바뀌면 거부한다.
4. API 계약과 회귀 테스트를 갱신한다. 필수만, 선택 근거 없음, 정확한 조합 없음, 환경 변경으로 `STALE`, 선택하지 않은 후보의 권한·파일 미포함을 확인한다.

## 변경 경계

`services/core-api`의 B 추천·Catalog·Preview 코드와 `contracts/openapi/core-api.yml`만 변경한다. A·AI·Frontend 소스와 운영 승인 Catalog는 변경하지 않는다.
