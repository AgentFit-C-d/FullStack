# Catalog: 릴리스·스키마·호환성

## 이 폴더의 설계 기록

| 파일 | 다루는 내용 |
| --- | --- |
| [릴리스 검증](catalog-release-validation-plan.md) | manifest, 파일별 SHA-256, 전체 Catalog 해시, 경로·크기·심볼릭 링크 거부 |
| [의미 검증](catalog-semantic-parser-plan.md) | 6개 JSON 파일의 합성 v1 스키마, 참조·중복·근거 검사 |
| [정확한 대상 조합](targeted-combinations-plan.md) | OS/Client/버전별 다중 도구 조합과 검증 근거 |
| [Claude Code 공식 근거 조사](claude-code-evidence-seed.md) | 공식 구성 문서와 실제 릴리스 검증에 필요한 증거·미수행 항목 |
| [Playwright MCP 후보 파일럿](playwright-mcp-pilot-2026-10-10.md) | 고정 npm 버전의 로컬 MCP 시작·도구 목록 확인과 미검증 항목 |
| [skills.sh 후보 수집](skills-sh-discovery.md) | 외부 API 검색, 변경 감지, 검토 대기 파일과 승인 경계 |
| [승인 Catalog 조회](catalog-read-model.md) | OS·Client·Capability 필터와 지원 행별 검증 근거의 내부 읽기 모델 |

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/`: `CatalogBundleLoader` → `CatalogSemanticParser` → `ParsedCatalog`가 릴리스를 읽는다. `CatalogStaticTemplateRenderer`와 `CatalogPreviewAssembler`는 이 검증 결과를 사용하므로 같은 패키지에 두었다. Preview 흐름은 [설정 폴더](../configuration/README.md)에 정리했다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/model/`: `CatalogRelease`, `CatalogTool`, `ToolSupport`, `VerifiedCombination`, `CatalogValidator`가 도구 지원·조합 모델을 표현하고 검사한다.
- `services/core-api/src/test/java/com/agentfit/coreapi/catalog/`: 매니페스트·의미 검증·정적 템플릿·Preview 연결 테스트.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/discovery/`: skills.sh 후보 검색 및 별도 JSON 스냅샷 갱신. 추천 엔진이 읽는 검증된 Catalog 릴리스와 연결하지 않았다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/CatalogRecommendationWorkflow.java`: 승인 Catalog, AI Capability 검증, 추천 판정을 묶는 B 내부 진입점. [연결 경계](../recommendation/catalog-recommendation-workflow.md)를 따른다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/CatalogReadService.java`: 승인 Catalog만 대상으로 한 읽기 필터와 검증 근거 투영. 공개 API는 아직 없다.

현재 테스트 데이터는 합성 예시다. 1차 실제 Catalog는 Claude Code부터 검증하고 Codex는 후속이다. 실제 지원 버전, 템플릿, 권한 매핑, 조합을 검증한 릴리스가 없으므로 제품에서 지원으로 표시하면 안 된다. 다음 작업은 [실제 릴리스 검증](../NEXT-STEPS.md)의 3번이다.
