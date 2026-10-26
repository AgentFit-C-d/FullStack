# FSB-05 Catalog 위험도·인증 검토 정보 구현 계획

> 기존 Catalog v1의 승인 경계를 유지한다. 이 계획은 B API의 근거 있는 정보 전달까지만 다루며, 실제 도구 검토·승인을 대신하지 않는다.

## 설계

- `tools.json`의 각 도구는 선택적으로 `riskReview`를 가진다. 없으면 API에서 `UNASSESSED`로 표시한다.
- `riskReview`가 있으면 `level`(LOW/MEDIUM/HIGH), `externalAuth`(REQUIRED/NOT_REQUIRED), `reason`, HTTPS `evidenceUrl`, ISO `checkedAt`을 모두 요구한다. 부분 입력, 알 수 없는 값, 근거 없는 위험도는 거부한다.
- 위험도는 도구/권한 이름으로 계산하지 않는다. 승인된 Catalog 해시를 통과한 검토 기록을 Catalog 조회와 추천 항목에 그대로 투영한다.
- 기존 합성/후보 Catalog는 `riskReview`가 없으므로 `UNASSESSED`다. 운영 지원 완료로 세지 않는다.

## 작업

1. 파서 테스트에 과거 v1 도구의 `UNASSESSED`, 검토 기록 유지, 잘못된/부분 기록 거부를 먼저 추가해 실패를 확인한다.
2. `ToolRiskReview`와 Catalog 파서·`ParsedCatalog`를 구현한다. 검토 기록은 Catalog 도구 키에 묶는다.
3. Catalog 조회·추천 생성·최신 조회 API 테스트에 검토 정보 투영을 추가해 실패를 확인하고 코드를 연결한다.
4. OpenAPI 계약·작성 가이드를 갱신하고 관련 Java 회귀 테스트를 실행한다. Docker가 가능하면 PostgreSQL/HTTP도 확인한다.

## 경계

- A, AI, Frontend 소스와 승인된 실제 Catalog 파일은 수정하지 않는다.
- 위험도/인증 검토 정보는 권한 승인, 외부 인증 완료, 설치 완료를 뜻하지 않는다.
