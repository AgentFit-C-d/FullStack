# Catalog: 릴리스·스키마·호환성

## 이 폴더의 설계 기록

| 파일 | 다루는 내용 |
| --- | --- |
| [릴리스 검증](catalog-release-validation-plan.md) | manifest, 파일별 SHA-256, 전체 Catalog 해시, 경로·크기·심볼릭 링크 거부 |
| [의미 검증](catalog-semantic-parser-plan.md) | 6개 JSON 파일의 합성 v1 스키마, 참조·중복·근거 검사 |
| [정확한 대상 조합](targeted-combinations-plan.md) | OS/Client/버전별 다중 도구 조합과 검증 근거 |

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/`: `CatalogBundleLoader` → `CatalogSemanticParser` → `ParsedCatalog`가 릴리스를 읽는다. `CatalogStaticTemplateRenderer`와 `CatalogPreviewAssembler`는 이 검증 결과를 사용하므로 같은 패키지에 두었다. Preview 흐름은 [설정 폴더](../configuration/README.md)에 정리했다.
- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/model/`: `CatalogRelease`, `CatalogTool`, `ToolSupport`, `VerifiedCombination`, `CatalogValidator`가 도구 지원·조합 모델을 표현하고 검사한다.
- `services/core-api/src/test/java/com/agentfit/coreapi/catalog/`: 매니페스트·의미 검증·정적 템플릿·Preview 연결 테스트.

현재 테스트 데이터는 합성 예시다. 실제 Codex/Client 지원 버전, 템플릿, 조합을 검증한 릴리스가 없으므로 제품에서 지원으로 표시하면 안 된다. 다음 작업은 [실제 릴리스 검증](../NEXT-STEPS.md)의 3번이다.
