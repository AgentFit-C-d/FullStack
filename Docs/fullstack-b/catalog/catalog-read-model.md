# 승인 Catalog 읽기 내부 모델

`CatalogReadService.list(releaseDirectory, approvedCatalogHash, filter)`는 해시·스키마 검증된 Catalog 파일에서 읽기 전용 목록을 만든다. OS, Client, Capability 필터를 적용하고 각 도구의 지원 행별 검증 단계와 근거 URL·확인일, 권한 매핑을 함께 돌려준다. `documentation`, `format`, `standalone`이 모두 PASS인 정확한 지원 행만 `verified=true`다. 다른 버전의 PASS를 현재 버전 지원으로 합치지 않는다.

이 모델은 인증된 `GET /api/tool-catalog` 구현의 B 내부 입력으로만 준비했다. API 응답 DTO·인증·소유권·오류 규칙은 A·Frontend 계약 후 연결한다. 현재 도구 데이터의 이름·종류·실제 Claude Code 근거는 아직 없으므로 Draft API의 완전한 응답을 제공하지 않는다. skills.sh 후보/SHORTLISTED 목록은 이 조회에 들어가지 않는다. 합성 릴리스 외 실제 승인 Catalog도 없다.
