# Catalog: 릴리스·스키마·호환성

## 후보 출처와 지원 대상

**skills.sh는 Skill 후보를 찾는 외부 출처이고, Claude Code는 첫 설정 적용 대상 Client다.** 후보 수집 → 변경 감지·검토 대기 → 출처·OS/Client 버전·권한·의존/충돌·설정 출력 검증 → 독립 승인 Catalog 릴리스 → 추천·Preview 순서로 연결한다. 외부 API의 검색 결과가 곧바로 제품의 검증된 Catalog나 추천 대상이 되지는 않는다. MCP 후보 출처 연동은 별도이며 현재 보류 중이다.

현재 구현은 skills.sh 검색 한 번에 최대 20개 Skill 후보를 받고 상세 해시를 확인해 별도 스냅샷에 저장한다. 전체 디렉터리 일괄 수집·운영 인증 연결·관리자 검토 UI·자동 승인 기능은 없다. [공식 API](https://www.skills.sh/docs/api)의 전체 목록도 무제한 추출 API가 아니라 인증과 페이지네이션을 요구하며, 필터 없는 all-time 목록에는 100,000행 제공 범위가 있다. 원 [기획 PRD](../../../../Docs/PRD.md)는 초기 제품 Catalog를 팀이 검증한 약 15~20개 도구로 시작하도록 정한다. 디렉터리 규모와 출시 가능한 도구 수를 동일하게 보지 않는다.

## 현재 확인할 문서

| 파일 | 다루는 내용 |
| --- | --- |
| [매니페스트 작성](catalog-manifest-authoring.md) | 릴리스 파일과 해시를 작성·검사하는 절차 |
| [Claude Code 공식 근거 조사](claude-code-evidence-seed.md) | 공식 구성 문서와 실제 릴리스 검증에 필요한 증거·미수행 항목 |
| [Playwright MCP 후보 파일럿](playwright-mcp-pilot-2026-10-10.md) | 고정 npm 버전의 로컬 MCP·Edge 탐색과 미검증 항목 |
| [skills.sh 후보 수집](skills-sh-discovery.md) | 외부 API 검색, 변경 감지, 검토 대기 파일과 승인 경계 |
| [승인 Catalog 조회](catalog-read-model.md) | OS·Client·Capability 필터와 지원 행별 검증 근거의 내부 읽기 모델 |

릴리스 파일·의미 검증·정확한 대상 조합을 구현할 때 사용한 상세 체크리스트는 [계획 기록](../archive/README.md)에 있다.

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/`: `CatalogBundleLoader` → `CatalogSemanticParser` → `ParsedCatalog`가 릴리스를 읽는다. `CatalogStaticTemplateRenderer`와 `CatalogPreviewAssembler`는 이 검증 결과를 사용하므로 같은 패키지에 두었다. Preview 흐름은 [설정 폴더](../configuration/README.md)에 정리했다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/model/`: `CatalogRelease`, `CatalogTool`, `ToolSupport`, `VerifiedCombination`, `CatalogValidator`가 도구 지원·조합 모델을 표현하고 검사한다.
- `services/core-api/src/test/java/com/agentfit/coreapi/catalog/`: 매니페스트·의미 검증·정적 템플릿·Preview 연결 테스트.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/discovery/`: skills.sh 후보 검색 및 별도 JSON 스냅샷 갱신. 추천 엔진이 읽는 검증된 Catalog 릴리스와 연결하지 않았다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/CatalogRecommendationWorkflow.java`: 승인 Catalog, AI Capability 검증, 추천 판정을 묶는 B 내부 진입점. [연결 경계](../recommendation/catalog-recommendation-workflow.md)를 따른다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/CatalogReadService.java`: 승인 Catalog만 대상으로 한 읽기 필터와 검증 근거 투영. 공개 API는 아직 없다.
- `ServerConfiguredCatalogReader`와 `CatalogSourceConfiguration`: 서버 설정 `agentfit.catalog.directory`와 `agentfit.catalog.approved-hash`가 둘 다 있을 때만 승인 Catalog 포트를 만든다. 호출마다 파일·의미·해시를 다시 검증하며 불일치 시 제공하지 않는다. 환경 변수로 설정한다면 `AGENTFIT_CATALOG_DIRECTORY`와 `AGENTFIT_CATALOG_APPROVED_HASH`를 사용한다. 후보의 manifest hash를 이 승인 값으로 자동 복사하지 않는다.

현재 테스트 데이터는 합성 예시다. 1차 실제 Catalog는 Claude Code부터 검증하고 Codex는 후속이다. 실제 지원 버전, 템플릿, 권한 매핑, 조합을 검증한 릴리스가 없으므로 제품에서 지원으로 표시하면 안 된다. 다음 작업은 [실제 릴리스 검증](../NEXT-STEPS.md)의 3번이다.
