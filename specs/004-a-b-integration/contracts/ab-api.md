# A-B 공개 API 계약 초안

상태: Phase 2 계획 기준. 구현과 fixture가 통과하기 전까지 확정 계약으로 보지 않는다.

## 추천

`POST /api/projects/{projectId}/recommendations`

- 서버가 인증 주체와 project 소유권을 검증한다.
- A confirmed snapshot과 현재 basis를 읽는다.
- 외부 AI 호출 후 저장 직전에 basis를 재검증한다.
- 성공 시 B recommendation과 success Audit을 함께 저장한다.

`GET /api/projects/{projectId}/recommendations/latest`

- AI를 재호출하지 않는다.
- project 소유권과 최신 basis를 검증한다.
- 다른 project의 recommendation ID를 조회할 수 없다.

## Preview

`POST /api/projects/{projectId}/config-previews`

- recommendation 소속, basis, Environment, 선택 도구, Catalog hash를 재검증한다.
- 원문·bytes·Diff는 저장하지 않고 B metadata/fingerprint만 저장한다.
- Preview metadata와 success Audit은 같은 transaction에 저장한다.

## 공통 실패

소유권 오류, 없는 객체, stale basis, 미확정 Profile, Catalog unavailable, AI 장애/형식 오류, 지원되지 않는 조합, 저장 실패는 성공 결과로 변환하지 않는다.

