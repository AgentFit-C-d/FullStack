# Catalog 릴리스 매니페스트 작성

`tools/catalog_manifest.py`는 **이미 검토한 릴리스 후보 파일**의 SHA-256과 v1 `manifest.json`을 작성하는 개발 도구다. Java `CatalogBundleLoader`의 고정 해시 계산 방식과 동일한 결과를 낸다. 실제 Client 동작, 지원 범위, 권한, 조합을 시험하거나 Catalog를 승인하지 않는다.

## 사용

릴리스 후보 디렉터리에 `capabilities.json`, `tools.json`, `support-matrix.json`, `relations.json`, `permissions.json`, `client-capabilities.json`을 넣는다. 필요한 정적 출력은 `templates/` 또는 `guides/` 아래에 둔다. 기존 `manifest.json`이 없는 디렉터리에서 실행한다.

```powershell
python tools/catalog_manifest.py <후보-디렉터리> <릴리스-ID>
```

명령은 계산된 `catalogHash`를 표준 출력에 표시하고 매니페스트를 생성한다. 누락·미등록 파일, 경로 이탈, 링크, 중복 경로, 잘못된 UTF-8, 파일/전체 크기 초과, 기존 매니페스트가 있으면 실패한다. 릴리스 내용이 바뀌면 기존 매니페스트를 수정하지 말고 새 후보 디렉터리와 새 릴리스 ID로 다시 작성한다.

작성 후 다음 명령으로 같은 B Java 로더·의미 검증기를 거친다. 검증된 지원 행이 있는 도구마다 정적 템플릿·가이드의 출력 가능 여부와 Preview 크기 한도를 확인하고, 검증된 전체 조합도 합산 출력 한도를 확인한다. `CANDIDATE_SCHEMA_VALID`는 **파일 무결성, v1 데이터 참조·근거 필드 형식, 정적 출력의 내부 규칙 통과**만 뜻한다. 명령이 출력한 해시가 매니페스트 작성 단계의 값과 일치해야 한다.

```powershell
cd services/core-api
mvn '-Dspring-boot.run.main-class=com.agentfit.coreapi.catalog.CatalogCandidateValidatorCommand' '-Dspring-boot.run.arguments=<후보-디렉터리-절대경로>' spring-boot:run
```

이어 실제 Claude Code의 정확한 OS·버전에서 문서·파일 형식·단독 동작·조합·권한 결과를 기록하고, 별도 검토자가 **독립 승인 해시**를 서버 설정 또는 신뢰된 저장소에 제공해야 한다. 어느 도구가 출력한 해시도 자동 승인 값으로 사용하지 않는다. 현재 실제 릴리스와 독립 승인 값은 없다.

테스트: `python -m unittest discover -s tools/tests -p 'test_*.py' -v`. 알려진 해시 벡터는 Java `CatalogBundleLoaderTest`의 값과 교차 확인한다. Java 후보 명령은 `CatalogCandidateValidatorCommandTest`에서 정상·무결성 실패·해시는 유효하지만 의미 스키마가 잘못된 입력·누락/과대 출력·조합 출력 초과를 확인한다.
