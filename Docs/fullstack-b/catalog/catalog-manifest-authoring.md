# Catalog 릴리스 매니페스트 작성

`tools/catalog_manifest.py`는 **이미 검토한 릴리스 후보 파일**의 SHA-256과 v1 `manifest.json`을 작성하는 개발 도구다. Java `CatalogBundleLoader`의 고정 해시 계산 방식과 동일한 결과를 낸다. 실제 Client 동작, 지원 범위, 권한, 조합을 시험하거나 Catalog를 승인하지 않는다.

## 사용

릴리스 후보 디렉터리에 `capabilities.json`, `tools.json`, `support-matrix.json`, `relations.json`, `permissions.json`, `client-capabilities.json`을 넣는다. 필요한 정적 출력은 `templates/` 또는 `guides/` 아래에 둔다. 기존 `manifest.json`이 없는 디렉터리에서 실행한다.

```powershell
python tools/catalog_manifest.py <후보-디렉터리> <릴리스-ID>
```

명령은 계산된 `catalogHash`를 표준 출력에 표시하고 매니페스트를 생성한다. 누락·미등록 파일, 경로 이탈, 링크, 중복 경로, 잘못된 UTF-8, 파일/전체 크기 초과, 기존 매니페스트가 있으면 실패한다. 릴리스 내용이 바뀌면 기존 매니페스트를 수정하지 말고 새 후보 디렉터리와 새 릴리스 ID로 다시 작성한다.

작성 후 다음 명령으로 같은 B Java 로더·의미 검증기를 거친다. 템플릿 인덱스를 제출했다면 지원 검증 상태가 `NOT_RUN`이어도 모든 후보 도구의 정적 출력 가능 여부와 Preview 크기 한도를 확인한다. 인덱스가 없으면 검증된 지원 행을 가진 도구를 거부하고, 미검증 도구만 있는 후보에는 `OUTPUT_REVIEW_REQUIRED`를 남긴다. 검증된 전체 조합도 합산 출력 한도를 확인한다. `CANDIDATE_SCHEMA_VALID`는 **파일 무결성, v1 데이터 참조·근거 필드 형식, 제출된 정적 출력의 내부 규칙 통과**만 뜻한다. 명령이 출력한 해시가 매니페스트 작성 단계의 값과 일치해야 한다.

```powershell
cd services/core-api
mvn '-Dspring-boot.run.main-class=com.agentfit.coreapi.catalog.CatalogCandidateValidatorCommand' '-Dspring-boot.run.arguments=<후보-디렉터리-절대경로>' spring-boot:run
```

이어 실제 Claude Code의 정확한 OS·버전에서 문서·파일 형식·단독 동작·조합·권한 결과를 기록하고, 별도 검토자가 **독립 승인 해시**를 서버 설정 또는 신뢰된 저장소에 제공해야 한다. 어느 도구가 출력한 해시도 자동 승인 값으로 사용하지 않는다. 현재 실제 릴리스와 독립 승인 값은 없다.

후보 검증 명령은 `CANDIDATE_SCHEMA_VALID` 뒤에 `RELEASE_NOT_READY reasons=...`도 출력한다. `SUPPORT_MISSING`은 지원 행 자체가 없는 도구가 있음을, `SUPPORT_NOT_RUN`은 지원 행의 검사가 남았음을, `SUPPORT_FAILED`는 지원 검사 실패가 기록됐음을 뜻한다. `PERMISSION_REVIEW_REQUIRED`는 매핑이 없는 도구에 대해 실제로 설정할 권한이 없는지 검토해야 한다는 뜻이다. `OUTPUT_REVIEW_REQUIRED`는 정적 출력 인덱스가 없음을, `COMBINATION_REVIEW_REQUIRED`는 여러 도구가 있으나 검증된 조합이 전혀 없음을 알린다. 이는 검토 항목을 찾기 위한 보수적인 신호이며, 모든 도구에 권한 매핑이나 모든 도구 쌍의 조합이 필요하다는 판정은 아니다. 독립 승인 여부는 명령이 확인할 수 없으므로 항상 `INDEPENDENT_APPROVAL_REQUIRED`가 표시된다. 따라서 이 명령은 `RELEASE_READY`를 출력하지 않는다.

검토가 남은 도구마다 `TOOL_REVIEW_REQUIRED toolKey=... reasons=...`도 도구 키 순서로 출력한다. 지원 행 누락·미실행·실패, 권한 매핑 검토, 정적 출력 인덱스 누락을 도구별로 찾기 위한 안내다. 이 줄이 없는 도구도 정확한 Client 동작·조합·독립 승인이 완료됐다는 뜻은 아니다. 현재 Playwright MCP 후보는 세 지원 검사가 `NOT_RUN`이고 권한 매핑·출력 인덱스가 없어 해당 도구에 세 가지 검토 이유가 표시된다.

테스트: `python -m unittest discover -s tools/tests -p 'test_*.py' -v`. 알려진 해시 벡터는 Java `CatalogBundleLoaderTest`의 값과 교차 확인한다. Java 후보 명령은 `CatalogCandidateValidatorCommandTest`에서 정상·무결성 실패·해시는 유효하지만 의미 스키마가 잘못된 입력·누락/과대 출력·조합 출력 초과를 확인한다.

## 두 후보의 재검토 범위 비교

기존 후보와 수정 후보가 있을 때 아래 로컬 명령은 두 매니페스트의 해시 및 의미 스키마를 검증한 뒤 다시 검토할 도구·조합을 출력한다. 도구 버전·지원 행·권한 매핑/근거·관계 변경은 해당 도구를 표시한다. 정적 템플릿/가이드 또는 전역 Capability 선언이 바뀌면 영향을 좁게 증명할 수 없으므로 모든 도구를 보수적으로 표시한다. 해당 도구를 포함한 검증 조합도 다시 표시한다.

```powershell
cd services/core-api
mvn '-Dspring-boot.run.main-class=com.agentfit.coreapi.catalog.CatalogRevisionImpactCommand' '-Dspring-boot.run.arguments=<기존-후보-절대경로> <수정-후보-절대경로>' spring-boot:run
```

`TOOL_REVIEW_REQUIRED`와 `COMBINATION_REVIEW_REQUIRED`는 변경 영향 조사 목록이다. `CATALOG_NOT_APPROVED`가 항상 출력되며, 이 명령은 지원 상태를 `PASS`로 바꾸거나 릴리스를 활성화하지 않는다. 2026-10-12에 원본 Playwright MCP 후보와 안내 초안 후보를 비교했을 때 정적 안내 파일 추가로 `playwright-mcp` 재검토가 보고됐다.
