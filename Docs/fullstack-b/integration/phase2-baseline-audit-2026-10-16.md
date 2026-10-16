# Phase 2 시작 기준 및 A·B·AI 연결 점검 (2026-10-16)

## 현재 Git 기준

- `origin/develop` = `66a7d6a` (A/B Phase 1과 AB 정리 병합). 로컬 `develop`을 fast-forward 확인한 뒤 `feat/fullstack-b-phase2`를 여기서 생성했다.
- `origin/main` = `1046f46` (A Phase 2 PR #6 병합). 이 변경은 **아직 `origin/develop`에 없다**. 따라서 현재 B Phase 2 브랜치에도 A Phase 2 코드가 없다.
- `origin/main`의 `specs/004-a-b-integration/spec.md`는 Draft이고 `tasks.md`의 T008–T036 대부분이 미완료다. main에 있는 A adapter/route를 통합 완료로 간주하면 안 된다.
- `git merge-tree --write-tree --name-only HEAD origin/main`의 비파괴 병합 시뮬레이션에서 문서, OpenAPI, 인증·프로젝트·Audit 코드, Catalog snapshot, 테스트 fixture 등 다수의 add/add 및 content 충돌이 확인됐다. main을 단순 병합할 수 없다.
- `Docs/fullstack-b/presentation/`은 기존 미추적 발표 자료다. 이 점검에서 수정하거나 정리하지 않았다.

## 연결 상태

| 경계 | develop에 있는 것 | main의 A Phase 2 변경 | 현재 판정 |
| --- | --- | --- | --- |
| 인증·프로젝트 | GitHub OAuth, 소유권 검사, Phase 1 프로젝트/분석 | USER·PROJECT UUID 전환과 repository 변경 | 병합·기존 코드 적응·실제 DB 검증 필요 |
| 기획·역할·환경 snapshot | B `OwnerCheckedSnapshot` 포트와 버전 basis | `project_profile`, `profile_confirmation_event`, `developer_profile`, `environment_profile` DDL 및 reader adapter | develop에는 A Phase 2 테이블·adapter 없음. 확정 데이터 생산 경로도 확인 필요 |
| AI Capability | B 9종 검증·추천 로직 | 기존 A `AiAnalysisClient`를 재사용하는 adapter | **실제 AI 계약 불일치**. 현재 ai-service는 `CONTRACT_TEST_DOUBLE`만 반환하고 OpenAPI `result`는 `kind`만 허용. adapter는 `claims`/`questions`를 요구 |
| 추천 저장/조회 | B1–B4 JDBC writer/reader와 내부 서비스 | UUID B9 스키마, 저장 adapter, 공개 controller | 프로젝트 UUID 바인딩 불일치; Catalog 승인 데이터도 미확정 |
| Preview | B5–B8 생성·검증·저장 코드 | Context/Store adapter와 일부 공개 경로 | UUID 조회 불일치 및 저장 직전 최신 basis 재확인 여부 확인 필요 |
| 승인·다운로드·이력 | B 내부 서비스 및 JDBC writer/reader | A Phase 2에는 연결 경로가 보이지 않음 | 인증된 API·현재 basis·Audit·ZIP 응답 연결 필요 |
| 프로젝트 삭제 | B 데이터 삭제 포트용 코드 | A 삭제 서비스에서 B deletion port 호출 | 프로젝트 UUID 바인딩 및 A/B 그래프 삭제 원자성 검증 필요 |

## 우선 해결할 타입·계약 충돌

1. **식별자**: develop의 A V1–V4와 B PostgreSQL/JDBC는 `PROJECT.id VARCHAR(36)`, 사용자 ID `BIGINT`를 전제로 한다. main의 `V5__agentfit_ab_uuid_schema.sql`은 `project.id`, `"USER".id`, B9의 프로젝트 FK와 승인/보고자 ID를 `UUID`로 재생성한다. V5는 개발 DB의 기존 데이터를 삭제하는 전제를 명시한다. 현재 B `ProjectDbId.require`는 임의의 36자 이하 문자열을 허용하고 `JdbcRecommendationMetadataWriter`, `JdbcLatestRecommendationReader`, `JdbcProjectBDataDeleter`, `JdbcStoredPreviewReader`, `JdbcConfigurationHistoryReader` 등이 프로젝트 ID를 `setString`으로 바인딩한다. 승인/보고 writer는 actor ID를 `setLong`으로 쓴다. **PostgreSQL UUID 컬럼에 대한 읽기·쓰기·삭제 전체 경로를 함께 바꿔야 한다.** 이 불일치는 코드와 DDL 대조 결과이며 실제 통합 DB 실행으로 아직 재현 검증하지 않았다.
2. **AI wire contract**: `contracts/openapi/ai-service.yml`과 `services/ai-service/app/main.py`의 응답은 테스트 더블 `result.kind`뿐이다. main의 `AiCapabilityAnalyzerAdapter`는 `result.claims`와 `result.questions`를 역직렬화한다. 고정 9개 Capability, 필요도, 근거, 질문 스키마와 오류 의미를 AI 담당과 맞추고 계약 테스트를 추가하기 전에는 실제 추천 성공 경로가 없다.
3. **Catalog**: skills.sh 수집은 후보 유입 경로다. 공식 근거·정확한 Client/OS/버전·조합·권한 매핑·독립 승인 해시가 확보된 릴리스만 추천 가능하다. 현재 파일럿 후보를 실제 지원 Catalog로 승격할 근거는 확인되지 않았다.
4. **신선도·원자성**: A의 추천 저장 adapter는 프로젝트 version을 `FOR UPDATE`로 재확인한다. Preview Context adapter는 조회 시 version을 검사하지만 Preview Store adapter는 B writer와 Audit 저장 전에 A의 현재 project/profile/review/developer/environment 버전을 다시 읽는 코드가 보이지 않는다. 이 경계는 동시 수정 테스트로 확인·보강해야 한다. B writer가 확인하는 저장 추천 basis만으로 A 현재 snapshot의 불변성을 증명하지 못한다.
5. **삭제와 Audit**: UUID 전환 후 B child→Audit→A child→PROJECT 삭제 순서, 외부 트랜잭션 공유, 실패 시 전체 rollback과 삭제 후 늦게 도착한 결과 거부를 PostgreSQL에서 확인해야 한다.

## 실행 순서와 완료 조건

1. 팀의 기준 브랜치를 맞춘다. A Phase 2를 `develop`에 병합/재정리할 때 충돌을 파일별로 해결하고, V5의 **개발 DB 폐기 전제**를 팀에 명시한다. 현재 B 브랜치를 main 위로 무작정 병합하지 않는다.
2. UUID가 최종 결정이면 B public service/DTO 계약을 가능한 유지하면서 JDBC 경계의 프로젝트·사용자 ID 전체를 UUID로 맞춘다. 반대 결정이면 A V5/adapter를 수정한다. 한쪽만 바꾸지 않는다. 새 빈 PostgreSQL에서 V1–V5 migration 및 B1–B9 왕복·삭제 통합 테스트로 판정한다.
3. A의 CONFIRMED Profile/confirmation event, Developer/Environment 저장 경로와 B snapshot reader를 실제 데이터로 검증한다. 소유권·타 프로젝트·미확정·버전 변경 실패 케이스를 포함한다.
4. AI Capability v1의 요청/응답 OpenAPI, 9종 완전성, `OPTIONAL` 대 불필요, 질문 담당, malformed/timeout 처리를 합의하고 FastAPI 구현 및 계약 테스트와 함께 연결한다.
5. 승인 Catalog가 준비되면 인증된 추천 생성/최신 조회 → Preview 생성 → 부분 구성 미충족 경고를 유지한 승인·ZIP 다운로드/적용 안내 순으로 연결한다. 각 저장 단계에서 A 현재 basis, 추천 소속, Catalog hash, Audit 원자성을 검증한다.

## 검증 범위

- 이번 점검은 Git 원격 ref를 fetch한 뒤 소스·DDL·OpenAPI·작업 체크리스트를 **정적 대조**하고 비파괴 merge-tree를 실행했다. 실제 merge, migration, 서버 실행은 하지 않았다.
- 이 호스트의 기본 `java`는 17이고 JDK 21/Maven은 PATH에 없다. 로컬 AI pytest도 완료 결과를 얻지 못해 중단했다. 따라서 현재 브랜치의 새 Java/Python 통과 결과는 주장하지 않는다. `Docs/fullstack-b/STATUS.md`의 440개 Java 테스트는 이전 실행 기록이다.
