# 승인 Catalog 읽기 내부 모델

`CatalogReadService.list(releaseDirectory, approvedCatalogHash, filter)`는 해시·스키마 검증된 Catalog 파일에서 읽기 전용 목록을 만든다. OS, Client, **정확한 Client 버전**, Capability 필터를 적용하고 각 도구의 지원 행별 검증 단계와 근거 URL·확인일, 권한 매핑을 함께 돌려준다. 버전 필터에 맞는 지원 행이 없으면 해당 도구는 결과에서 제외한다. `documentation`, `format`, `standalone`이 모두 PASS인 정확한 지원 행만 `verified=true`다. 다른 버전의 PASS를 현재 버전 지원으로 합치지 않는다.

이 모델은 도구의 이름·종류(`PLUGIN`/`MCP`/`SKILL`/`HOOK`)·출처 URL도 반환한다. 이름·종류·출처가 없는 릴리스는 파싱 단계에서 거부한다. 이 정보는 해당 Client에서 동작한다는 검증 결과와 별개다.

인증된 `GET /api/tool-catalog`의 B 내부 입력으로만 준비했다. `AuthenticatedCatalogQueryService`는 A가 제공할 세션 확인을 먼저 요구하고 서버 승인 Catalog 소스에서만 읽는다. API 응답 DTO·실제 A 인증 어댑터·오류 규칙은 A·Frontend 계약 후 연결한다. 실제 Claude Code 검증 근거와 승인된 제품 Catalog가 없어 Draft API의 완전한 응답을 제공하지 않는다. skills.sh 후보/SHORTLISTED 목록은 이 조회에 들어가지 않는다.

Spring에서는 A가 `AuthenticatedCaller` 빈을 제공하고 서버 승인 Catalog의 경로·해시가 모두 설정된 경우에만 `AuthenticatedCatalogQueryService` 빈을 만든다. 어느 한쪽이 없으면 조회 서비스도 없으며 공개 HTTP 경로는 아직 등록하지 않는다.
