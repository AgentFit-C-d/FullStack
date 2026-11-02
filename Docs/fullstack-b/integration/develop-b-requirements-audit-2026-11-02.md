# develop 통합 후 B 기능·기획 대조 — 2026-11-02

## 기준과 결론

- 검증 커밋: `164e1e1` (`origin/develop`, PR #11 병합). 로컬 develop을 fast-forward로 최신화했다.
- 기획 기준: `D:/capstone/Docs/team-prds/full-stack-b/PRD.md`의 **FSB-01~12**, `Docs/AgentFit_AB_1차통합_개발기준.pdf` 45쪽. 특히 PDF 25~28쪽의 Catalog·환경·질문 계약을 대조했다.
- 후속 사용자 결정도 적용한다: Claude Code 우선, 부분 구성의 승인·다운로드 허용 및 경고 유지, 사용자가 고른 skills.sh 후보 ZIP 허용. 팀의 별도 승인을 받지 않았다는 사실 자체를 개발 중단 사유로 삼지 않는다.
- **A는 이미 병합됐으며 AI 코드도 존재한다. “A·AI 부재로 연동 전”은 현재 상태가 아니다.** 남은 항목은 통합 코드의 계약·설정·기능 누락이다.
- 12개 영역 모두 관련 코드가 있지만, 요구사항 전체가 빠짐없이 구현된 것은 아니다. 테스트 통과율을 제품 완성률로 환산하지 않는다. 과거 내부 B01~B12의 4/12와 PRD FSB-01~12를 혼용하지 않는다.

## 이번에 실행한 검증

| 범위 | 결과 | 증명하지 않는 것 |
| --- | --- | --- |
| Core `mvn -q verify` (Java 21) | 617건 중 552 통과, 65 조건부 생략, 실패·오류 0. 패키징 성공 | 생략된 실제 외부 호출·DB 사례 |
| 격리 PostgreSQL 17.11 | BPhase2UuidSchemaPostgresTest 3건, FinalizationApiV5PostgresTest 1건, LatestRecommendationV5PostgresTest 3건, ProjectDeletionPostgresIntegrationTest 1건: 총 8건 통과 | 운영 DB 이관·실제 사용자 브라우저 전체 흐름 |
| DB 마이그레이션 | 빈 격리 스키마에 V1~V10 적용 성공 | 과거 B V7 적용 DB의 자동 업그레이드 |
| 웹 `npm test -- --run` | Node 100건 + Vitest 9건 통과 | 실제 로그인 세션·화면 간 종단 시연 |
| 웹 `npm run build` | TypeScript 검사 및 index/analysis 두 진입점 빌드 성공 | 두 화면의 사용자 데이터 흐름 통합 |
| 통합 AI 내부 계약 테스트 | HMAC 인증·needs/claims·rank 관련 15건 통과 | 실제 Solar 추론·별도 AI 저장소 교체의 종단 성공 |

DB 테스트는 일회용 컨테이너/격리 스키마를 사용했고 종료 후 컨테이너를 제거했다. 사용자 DB·볼륨은 수정하지 않았다. 실제 Solar 호출, 외부 Skill 실행, skills.sh 라이브 가용성은 이번 점검에서 새로 실행하지 않았다. 이전 실행 기록은 이번 통과 증거로 합산하지 않는다. AI 테스트는 제한 환경에서 정지한 실행을 중단하고 로컬 서버 실행 권한으로 다시 실행해 통과했다. FastAPI lifespan/TestClient 폐기 예정 경고와 Flyway의 PostgreSQL 17 공식 검증 버전 경고는 남아 있다.

## 상위 12개 요구사항

| ID | 기능 | 현재 구현·확인 | 남은 범위 |
| --- | --- | --- | --- |
| FSB-01 | 카탈로그 관리 | 파일 릴리스·해시 확인, 실시간 skills.sh 검색, 후보 조회·별도 ZIP | 실물 추천 카탈로그는 현재 TDD Skill 1종 중심. 원 PRD 15~20종 범위 미충족. 검색 후보를 정규화·저장하고 추천 카탈로그에 반영하는 전체 운영 경로 없음 |
| FSB-02 | 의존·중복·충돌·조합 | CatalogValidator/ConfigurationSelectionValidator 및 전체 선택 조합 판정 구현 | 다양한 실제 Plugin/MCP/Skill 조합 데이터 부족. 카탈로그 기록 검사를 모든 도구의 실제 동시 실행 검증으로 설명하면 안 됨 |
| FSB-03 | 환경별 호환 추천 | 확정 Profile·역할·OS·Client·설치 목록을 읽어 9종 Capability와 대조 | 런타임·외부 인증 준비 상태 등의 입력·판정 범위 부족. 별도 AI의 needs 응답은 실제 추천 경로에 연결되지 않음 |
| FSB-04 | 결과 유형·최소/선택 구성 | 네 결과, 최소/부분 구성, 미충족 경고, 명시적 선택 Capability 재추천 구현 | 질문 답변 저장이 필요 정보를 해소하지 않음. AI 서비스의 현재 Capability는 키워드 규칙이며 별도 AI의 Solar needs/rank와 다름 |
| FSB-05 | 추천 근거·권한·위험도 | 저장된 Capability/근거/위험도·지원 정보와 선택 후보 제공 | 최신 분석 humanReview/pending이 확정 입력·추천 근거로 이어지는 통합 미완료. 검색 후보 관련성은 NAME_RULE/UNCONFIRMED이며 AI 내용 평가가 아님 |
| FSB-06 | 권한 의미와 Client 규칙 | 권한 옵션·정책 매핑·지원 여부 검사 구현 | 실제 권한·외부 인증 자료는 제한된 도구 범위. 새로운 skills.sh 후보에 자동으로 생성되는 정보가 아님 |
| FSB-07 | 선택·ASK/ALLOW/DENY | 선택 및 지원 정책 검사, 거부 처리 구현 | 실물 도구별 Client 정책 효과의 검증 범위 확대 필요. 사용자 선택을 실제 실행 허가 확인으로 취급하면 안 됨 |
| FSB-08 | 고정 설정 생성 | 고정 템플릿·허용 파일·Preview·ZIP 구현 | 모든 검색 Skill이 고정 템플릿 추천 경로로 자동 편입되는 것은 아님. 후보 ZIP은 별도 경로 |
| FSB-09 | 기존 설정 비교 | 제공 파일 한정 Diff·충돌·신규 설정안 처리 구현 | 공통 단계별 화면과 현재 /app/live의 기능 연결 확인 필요. 임의 자동 병합은 현재 제공하지 않음 |
| FSB-10 | 승인·산출물 일치 | basis·지문·만료 검사, 저장 승인 후 ZIP 생성. PostgreSQL 승인·ZIP 테스트 통과 | 신규 분석 화면을 시작점으로 한 실제 사용자 전체 흐름 확인 필요 |
| FSB-11 | 적용·복구 안내 | ZIP 안내·이력·사용자 APPLIED/FAILED 보고 API 구현 | 실제 Client 설치/인증/호출·복구 범위는 제한적. 사용자 보고와 자동 검증 결과를 구분해야 함 |
| FSB-12 | 변경 재평가·이력·삭제 | STALE, 카탈로그 변경 영향, 내용 없는 이력, A 프로젝트 삭제 연동 구현·DB 확인 | 자동 카탈로그 갱신/재평가 운영, 환경 입력 Audit, 과거 DB 마이그레이션 대응 보완 필요 |

## 우선 보완할 실제 불일치

### 1. 질문 답변은 주석 저장에 머무름 — 높은 우선순위

`recommendation/answers/RecommendationQuestionAnswerService.save`는 `recommendation_question_answer`에 문자열을 저장한다. Profile·Developer·Environment와 버전은 바꾸지 않는다. `OwnerCheckedSnapshotReaderAdapter` 및 `RecommendationCreationService`도 이 답변을 AI 입력으로 읽지 않는다.

PDF 23·28쪽은 notice/basis/답변 유형을 확인하고 원천값·버전·Audit를 같은 트랜잭션에 반영한 뒤 재추천하도록 정의한다. 현재 UI도 답변 저장 후 관련 입력을 별도로 고치라고 안내한다. **답변 저장 API 존재와 질문 해소 기능 완료는 다르다.**

### 2. 별도 AI 클라이언트는 작성됐지만 실제 호출 경로에 없음 — 높은 우선순위

현재 추천은 `AiCapabilityAnalyzerAdapter → AiAnalysisClient.needs → 통합 ai-service/app/ai_v2/needs.py` 경로다. 통합 AI는 키워드 규칙으로 9종 claims를 반환한다.

`D:/capstone/AgentFit_AI_v2/apps/ai/agentfit_ai/skill_discovery.py`는 Solar 기반 needs/rank를 제공하지만 Bearer 인증·budget·직접 needs 응답을 사용한다. B의 `ExternalAiNeedsClient`는 이 계약을 처리하는 클래스만 있고 실제 서비스 Bean/호출부가 없다. 코드 전체 검색에서 생성·호출 연결을 찾지 못했다. `SkillRankPreviewService`도 B 추천 저장과 분리돼 있다.

따라서 “AI 코드가 없어서 대기”가 아니라 **이미 있는 AI 계약에 맞춰 B 서비스 연결을 완성할 작업**이다. 별도 AI의 검색 필요와 B의 9종 Capability를 근거 없이 동일시하지 않는다.

### 3. 실행 구성에 따라 추천 AI 주소가 잘못됨 — 높은 우선순위

루트 `docker-compose.yml`은 분석 주소만 `http://ai:8001/internal/v1/analysis`로 설정한다. `scripts/dev-all.ps1`도 분석 주소만 기본 설정한다. `application.yml`의 needs/rank 기본값은 `localhost:8000`이므로 별도 환경변수로 지정하지 않으면 분석은 돼도 추천 AI 호출은 다른 주소로 향한다.

반면 `infra/compose.fullstack.yaml`은 세 주소를 명시한다. 설정 파일에 값이 있을 가능성은 있으므로 모든 실행 환경에서 무조건 실패한다고 단정하지 않는다. 배포 기본값과 문서화된 실행법을 일치시켜야 한다.

카탈로그도 directory/hash 설정이 있어야 관련 서비스가 활성화된다. 루트 Compose에는 카탈로그 볼륨이 없으므로 외부 파일 경로만 지정하는 것으로는 컨테이너 안에서 접근할 수 없다.

### 4. 새 분석 검토 저장과 B 확정 Profile 경로가 분리됨 — 높은 우선순위

`App.tsx`의 `/analysis.html`은 `PUT /analysis-results/latest/review`를 사용한다. `AnalysisReviewService.save`는 humanReview만 저장하고 `profile/confirm`을 호출하지 않는다. B는 `project_profile.kind='CONFIRMED'`와 confirmation event를 조회한다.

기존 `/app/live`에는 별도의 `POST /profile/confirm` 흐름이 있다. 따라서 **확정 API가 없는 것이 아니라, 새 화면의 검토 내용을 기존 확정 입력에 반영하여 B로 넘기는 경로가 통합되지 않은 것**이다. 새 화면 저장만으로 추천 가능한 상태라고 안내하면 안 된다.

### 5. 환경 입력이 1차 기준보다 축소됨 — 높은 우선순위

`RecommendationInputsService`는 OS·Client·Client 버전을 모두 비어 있지 않은 문자열로 요구한다. PDF는 모르는 버전을 미정으로 저장하고 필요한 경우 질문하도록 한다.

현재 환경 DTO에는 런타임·인증 준비 상태·기존 설정 확인 수준이 없다. 설치 목록 한도는 코드 50개, PDF 30개다. normalized_name에 원래 toolKey를 그대로 저장하며 trim/소문자 중복 검사도 없다. 문자열 제한은 `String.length()`로 UTF-16 길이를 세어 PDF의 Unicode code points와 다르다. 환경/역할 쓰기 서비스에는 Audit 기록 호출도 없다.

이는 자료 부족이 아니라 B가 수정할 수 있는 입력·저장 계약 차이다. 단순 버전 null 허용만으로 나머지 항목이 해결되지는 않는다.

### 6. skills.sh 후보와 실제 추천 카탈로그 사이가 아직 분리됨

`SkillsShCandidateCollector` 검색 → `SkillCandidateSuggestionController`의 이름 기반 관련성 → 후보 ZIP 경로는 있다. 하지만 `ServerConfiguredCatalogReader`와 `CatalogRecommendationWorkflow`는 별도 릴리스 파일을 읽는다. 검색 결과가 DB 카탈로그에 지속 저장되거나 자동으로 내부 조합 추천에 편입되지는 않는다.

원 PRD는 파일 기반 Catalog를 허용한다. 따라서 DB 미저장 자체를 원기획 위반으로 보지는 않는다. 다만 사용자가 후속 요청한 **수집 → 카탈로그 반영 → 조합 판정 → 추천** 전체 흐름은 미완료다. 보완에는 출처/내용 버전, 기능 매핑, 사용 조건의 알려짐/미정, 조합 판정과 제외 사유가 필요하며 팀 승인 대기만으로 설명하지 않는다.

### 7. 관련 검토·보류의 판정 입력이 축소됨

`OwnerCheckedSnapshotReaderAdapter`는 현재 review version을 읽지만 실제 관련 충돌은 `project_profile_gap`의 CONFLICT 개수로 판정한다. PDF의 Review 항목·fieldMetadata pending 전달/관련성 판정은 이 어댑터에서 확인되지 않는다. 모든 충돌을 한 bool로 넘기는 현재 방식과 세부 기준의 차이를 정리해야 한다. PDF가 제안 상태로 표시한 세부 JSON까지 모두 확정 요구로 간주하지는 않는다.

## 다음 구현 순서

1. 실행 구성의 needs/rank 주소·카탈로그 마운트를 정렬한다.
2. B 환경 DTO·미정 값·정규화·한도·Audit를 1차 계약에 맞춘다.
3. 질문 답변 coordinator에서 원천값과 버전을 원자적으로 수정한다.
4. 새 분석 검토 결과 → 기존 확정 API → B 추천으로 사용자 흐름을 연결한다.
5. 이미 존재하는 별도 AI needs/rank와 skills.sh 후보를 실제 추천 서비스에 연결한다.
6. 실제 추천 대상과 설정 지원 범위를 넓히며 사용자 시나리오로 확인한다.

이번 작업은 코드·DB 검증과 문서 갱신이며, 위 기능을 수정 완료한 작업이 아니다. A/AI 구현 파일은 변경하지 않았다.
