# Preview·설정·다운로드

## 구현 흐름

| 단계 | Java 진입점·현재 결과 |
| --- | --- |
| 1. 입력·Preview | `CatalogPreviewAssembler`가 승인 템플릿·현재 basis·선택·권한을 검사하고, `PreviewFileComparator`가 제공된 기존 파일만 비교한다. |
| 2. 지문·저장 | `PreviewFingerprint`가 입력·파일 해시를 묶고, `JdbcPreviewMetadataWriter`가 원문·Diff 없이 B5–B8 메타데이터를 기록한다. |
| 3. 최종 승인 | `CatalogReadyPreviewApprovalWorkflow`와 `JdbcPreviewApprovalWriter`가 READY Preview·만료·현재 지문을 확인한다. |
| 4. ZIP·이력 | `ConfigurationDownloadService`가 승인 Catalog로 동일 bytes를 재생성하고, `JdbcConfigurationExportWriter`가 B9 생성 이력을 기록한다. |
| 5. 사용자 보고 | `ConfigurationUserReportService`와 `JdbcConfigurationReportWriter`가 APPLIED/FAILED 진술을 USER 출처로 기록한다. |

각 단계의 이전 구현 계획과 시험 체크리스트는 [계획 기록](../archive/README.md), 현재 승인·ZIP 경계는 [승인 안내](export/approval-issuance.md)와 [Catalog 내보내기](export/catalog-approved-workflow.md)를 본다.

## 코드 위치

- `services/core-api/src/main/java/com/agentfit/coreapi/catalog/`: `CatalogStaticTemplateRenderer`, `CatalogPreviewRequest`, `CatalogPreviewAssembler`, `CatalogReadyPreviewApprovalWorkflow`, `CatalogApprovedConfigurationWorkflow`, `PreviewAssemblyLimits`. Catalog 릴리스와 밀접하게 연결된 조립·승인·내보내기 경계다.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/preview/`: 비교(`PreviewFileComparator`), 민감정보 검사, 지문(`PreviewFingerprint`)과 입력·결과 모델.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/export/`: 저장 Preview/승인 상태, 신선도 검사, 승인된 ZIP 경계와 내부 ZIP 작성기.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/report/`: 사용자 적용 진술의 내부 검증 경계.
- `services/core-api/src/main/java/com/agentfit/coreapi/configuration/history/`: 생성 이력의 최소 메타데이터와 CURRENT/STALE 판정.
- 같은 `configuration/history/`의 `ApprovedConfigurationGenerator`는 승인된 ZIP과 최소 이력의 생성 순서를 묶는다.
- 테스트는 `services/core-api/src/test/java/com/agentfit/coreapi/catalog/`, `configuration/preview/`, `configuration/export/`, `configuration/report/`, `configuration/history/`에 같은 단위로 배치했다.

현재는 **메모리 내 Preview·승인/내보내기·사용자 보고 검증 경계**까지만 연결됐다. 서버 저장·인증/소유권·감사·HTTP 다운로드 및 보고 API는 구현되지 않았다. 사용자 제공 원본과 Diff를 영속화해서는 안 되며, Secret 검사는 모든 비밀값을 찾는 보증이 아니다. [다음 작업](../NEXT-STEPS.md)의 4·5번에서 A 계약과 함께 완성해야 한다.
