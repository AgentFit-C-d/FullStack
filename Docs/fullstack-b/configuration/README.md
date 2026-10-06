# Preview·설정·다운로드

## 순서대로 읽는 설계 기록

| 단계 | 파일 | 현재 구현 |
| --- | --- | --- |
| 1. 정적 입력 | [Catalog 템플릿](assembly/catalog-static-template-plan.md) | 승인 해시와 매니페스트가 일치하는 텍스트만 Preview 후보로 복사 |
| 2. 생성 경계 | [Preview 조립](assembly/catalog-preview-assembly-plan.md) | Catalog·basis·선택·권한 확인 후 후보와 지문 연결 |
| 3. 비교 | [Preview 비교](preview/preview-comparison-plan.md) | 제공된 기존 파일만 Diff, 나머지는 신규 제안으로 표시 |
| 4. 민감정보 | [Secret 패턴 검사](preview/preview-sensitive-input-plan.md) | 알려진 민감값을 Diff/해시 전에 차단 |
| 4a. 크기 제한 | [Preview 크기 예산](assembly/preview-budget-plan.md) | 파일·선택 수, 파일별 글자 수, UTF-8 내용 합계를 미리 제한 |
| 5. 확정 대상 | [Preview 지문](preview/preview-fingerprint-plan.md) | 버전 basis·선택·내용 해시를 묶는 결정적 SHA-256 |
| 5a. 신선도 | [Preview 상태 재검사](preview/preview-freshness-plan.md) | 현재 basis·만료·저장/제출/재생성 지문·확인값 판정 |
| 6. 내보내기 | [ZIP 재생성](export/preview-zip-export-plan.md) | 지문이 같을 때 Preview 경로와 동일한 파일 bytes만 압축 |
| 6a. 승인 경계 | [승인된 ZIP 내보내기](export/approved-export-plan.md) | 저장 승인 ID·Preview ID·지문·만료를 재검사한 뒤 ZIP 생성 |
| 7. 사용자 진술 | [적용 보고 검증](report/user-application-report-plan.md) | APPLIED/FAILED·실패 사유를 제한하고 출처·시각을 서버가 지정 |

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/`: `CatalogStaticTemplateRenderer`, `CatalogPreviewRequest`, `CatalogPreviewAssembler`, `PreviewAssemblyLimits`. Catalog 릴리스와 밀접하게 연결된 조립 경계다.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/preview/`: 비교(`PreviewFileComparator`), 민감정보 검사, 지문(`PreviewFingerprint`)과 입력·결과 모델.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/export/`: 저장 Preview/승인 상태, 신선도 검사, 승인된 ZIP 경계와 내부 ZIP 작성기.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/report/`: 사용자 적용 진술의 내부 검증 경계.
- 테스트는 `services/core-api/src/test/java/com/agentfit/coreapi/catalog/`, `configuration/preview/`, `configuration/export/`, `configuration/report/`에 같은 단위로 배치했다.

현재는 **메모리 내 Preview·승인/내보내기·사용자 보고 검증 경계**까지만 연결됐다. 서버 저장·인증/소유권·감사·HTTP 다운로드 및 보고 API는 구현되지 않았다. 사용자 제공 원본과 Diff를 영속화해서는 안 되며, Secret 검사는 모든 비밀값을 찾는 보증이 아니다. [다음 작업](../NEXT-STEPS.md)의 4·5번에서 A 계약과 함께 완성해야 한다.
