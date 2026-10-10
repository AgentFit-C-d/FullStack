# Catalog 조사 후보

이 디렉터리는 **미승인 조사 데이터**다. `services/core-api`가 사용하는 제품 릴리스 경로로 배포하거나 `manifest.json`의 `catalogHash`를 독립 승인 값으로 등록하지 않는다. 해시는 파일 무결성만 증명하며 Client 지원·권한·조합 검증 결과가 아니다.

첫 후보 [`playwright-mcp-0.0.83-windows-claude-2.1.270`](playwright-mcp-0.0.83-windows-claude-2.1.270/)에는 Microsoft Playwright MCP의 고정 버전과 로컬 조사 대상만 넣었다. 지원 행의 `documentation`·`format`·`standalone`은 모두 `NOT_RUN`이고, 권한 매핑·검증된 조합·설정 템플릿은 비어 있다. [파일럿 기록](../Docs/fullstack-b/catalog/playwright-mcp-pilot-2026-10-10.md)에는 확인한 동작과 아직 확인하지 않은 범위가 구분돼 있다. 팀이 도구와 대상 버전을 채택하고 전체 검증을 끝내기 전에는 이 후보가 추천되거나 설정을 생성해서는 안 된다.

`tools/catalog_manifest.py`로 작성한 매니페스트의 hash는 `2bc5b6d0559d89733f0d2d3a18a0851af8150f51c4615e0205449479f33ec2f1`이다. `CatalogCandidateValidatorCommand`의 `CANDIDATE_SCHEMA_VALID`는 2026-10-10에 확인했다. 이 명령은 Client 실행 검증 또는 독립 승인을 수행하지 않는다. 후보 파일을 바꾸면 같은 릴리스 ID의 매니페스트를 덮어쓰지 말고 새 후보 디렉터리와 새 ID를 만든다.
