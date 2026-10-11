# 풀스택 B 코드 길잡이

기준 루트: `services/core-api/src/main/java/com/agentfit/coreapi/`. 테스트는 `src/test/java`에서 같은 패키지를 찾는다. 이 표는 구현 위치를 안내하며 Phase 1 완료 판정은 [STATUS.md](STATUS.md)를 따른다.

| 폴더 | 역할 | 먼저 볼 파일 |
| --- | --- | --- |
| `catalog/` | 검증된 릴리스 로딩·파싱, 정적 템플릿, Catalog를 사용하는 추천·Preview 흐름 | `CatalogBundleLoader`, `CatalogSemanticParser`, `CatalogRecommendationWorkflow`, `CatalogPreviewAssembler` |
| `catalog/model/` | 도구·지원 대상·검증 조합의 불변 모델과 검증 | `CatalogRelease`, `CatalogTool`, `CatalogValidator` |
| `catalog/discovery/` | skills.sh 외부 후보 수집·변경 탐지·검토 대기열. 승인 Catalog에는 직접 반영하지 않음 | `SkillsShCandidateCollector`, `SkillsShCandidateRefresh`, `SkillsShReviewQueue` |
| `recommendation/` | Capability와 설치 환경을 바탕으로 결정적 추천 결과 계산 | `RecommendationInputAssembler`, `RecommendationEngine`, `RecommendationDecision` |
| `recommendation/ai/` | AI 후보의 내부 9개 Capability 검증 | `AiCapabilityIntake` |
| `recommendation/selection/` | 추천 도구 선택, 의존성·충돌·권한 검증 | `ConfigurationSelectionValidator`, `PermissionPolicyGate` |
| `recommendation/persistence/` | B1–B4 추천 메타데이터 PostgreSQL 쓰기, 최신 추천 상세와 Preview가 참조하는 특정 추천 읽기. A가 소유권·현재 버전·Audit·커밋 담당 | `JdbcRecommendationMetadataWriter`, `JdbcLatestRecommendationReader` |
| `configuration/preview/` | 파일 비교·민감 입력 검사·지문 생성 | `PreviewFileComparator`, `PreviewFingerprint`, `PreviewSensitiveContentGuard` |
| `configuration/export/` | 승인 유효성·만료 검사와 ZIP 재생성 | `PreviewApprovalIssuer`, `ApprovedPreviewZipExporter` |
| `configuration/history/` | 생성 이력 메타데이터·조회 투영 | `ApprovedConfigurationGenerator`, `ConfigurationHistoryQueryService` |
| `configuration/report/` | 사용자 적용 진술 검증·서비스 경계 | `ConfigurationUserReport`, `ConfigurationUserReportService` |
| `configuration/persistence/` | B5–B9 PostgreSQL 메타데이터 쓰기·Preview/승인·생성 이력/보고 읽기·B1–B9 프로젝트 데이터 삭제. A가 소유권 검사·현재 버전·Audit·커밋 담당 | `JdbcPreviewMetadataWriter`, `JdbcStoredPreviewReader`, `JdbcConfigurationExportWriter`, `JdbcConfigurationHistoryReader`, `JdbcConfigurationReportWriter`, `JdbcProjectBDataDeleter` |

Catalog 관련 상위 서비스는 현재 `catalog/`에 함께 있다. A·AI의 실제 계약과 라우트가 아직 없으므로 공개 인터페이스를 바꾸는 패키지 분할은 이번 정리에 포함하지 않았다. `configuration/persistence/`의 네 JDBC 쓰기 클래스와 PostgreSQL 테스트는 Preview·설정 책임으로 함께 이동했다.

DB 구조는 `services/core-api/src/main/resources/db/postgresql/b-schema.sql`과 [PostgreSQL 인계](db/postgresql-schema-handoff.md)를 본다. 로컬 DB 실행·스모크 검증은 `infra/postgres/`에 있다. 발표용 이미지와 스크립트는 별도 `presentation/` 영역이며 이번 정리 대상에서 제외했다.
