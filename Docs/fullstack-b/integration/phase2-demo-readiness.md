# Full Stack B Phase 2 데모 준비 상태 (2026-10-16)

## 바로 시연 가능한 검증과 아직 연결되지 않은 흐름

| 구간 | 상태 | 근거 / 남은 일 |
| --- | --- | --- |
| B 추천·Preview·승인·이력·삭제의 UUID 저장 | **검증됨** | `BPhase2UuidSchemaPostgresTest`가 격리된 PostgreSQL에 A Flyway V1–V5를 적용해 추천의 9개 Capability, Preview, 승인자 UUID, B9 export/USER 보고, B 삭제 및 Audit 실패 rollback을 검사한다. |
| B 내부 추천·설정 규칙 회귀 | **검증됨** | Maven 전체 456개 테스트 통과, 실패/오류 0, 건너뜀 3. JDK 24에서 Java 21 release로 빌드했으며 Mockito의 자체 부착 오류를 Byte Buddy 시작 에이전트로 해결했다. JDK 21 실행 결과는 아직 없다. |
| A 로그인 → 추천 생성/최신 조회 → Preview 공개 API | **부분 연결** | `RecommendationController`와 A adapter가 있고 Spring context 및 기존 프로젝트/분석 WebMvc 테스트는 통과했다. 이 컨트롤러의 요청·응답과 실제 A UUID 데이터까지 통과한 HTTP 테스트는 아직 없다. |
| AI Capability 실제 응답 | **차단** | AI 담당: 9종 claim·question·선택/불필요 구분·오류 응답을 OpenAPI와 FastAPI에 구현해 A adapter와 계약 테스트로 맞춰야 한다. 현재는 `CONTRACT_TEST_DOUBLE`이다. |
| 실제 승인 Catalog | **차단** | B/검증 담당: 첫 Client/OS/버전과 도구·권한·정적 템플릿·조합의 실제 검증 근거, 독립 승인 hash가 필요하다. skills.sh 후보를 자동으로 활성화하지 않는다. |
| Preview 저장 시 동시 변경 차단 | **미완** | A 담당: `JdbcPreviewStoreAdapter`의 동일 트랜잭션에서 현재 project/profile/review/developer/environment basis를 재조회·비교하고 동시 수정 테스트를 추가해야 한다. B writer의 추천 basis 검증만으로 A 현재 상태를 보장하지 않는다. |
| 최종 승인·ZIP 다운로드/적용 안내 HTTP | **미완** | A+B 공동: 인증/소유권, 현재 basis, Preview 만료·fingerprint, 승인·B9/Audit 원자성, 승인된 Catalog 재생성을 공개 경로로 묶어야 한다. 부분 구성을 허용하되 미충족 경고를 유지한다. |

## 재현한 검증 환경

- 브랜치: `feat/fullstack-b-phase2`, 기준 `origin/main` `1046f46`.
- 테스트 DB: 전용 임시 PostgreSQL 17 컨테이너 `agentfit-b-phase2-test`; 매 테스트가 임의의 독립 schema를 만들고 삭제한다. 기존 개발 DB에 파괴적인 V5를 적용하지 않았다.
- 실행: `services/core-api`에서 `AGENTFIT_TEST_POSTGRES_URL`, 사용자, 비밀번호를 전용 DB에 설정하고 Maven `test` 실행. Java 24 사용 시 Surefire JVM에 `-javaagent:<byte-buddy-agent-1.14.19.jar> -Dnet.bytebuddy.experimental=true`를 전달했다.
- 결과: `Tests run: 456, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`. 건너뛴 항목은 Windows에서 심볼릭 링크 생성 권한을 요구하는 Catalog 테스트 1개와 Testcontainers가 Docker를 사용할 수 없다고 판정한 A의 PostgreSQL 동시성 테스트 2개다. 전체 기능 시연 성공을 뜻하지 않는다.

다음 데모의 성공 기준은 검증된 Catalog와 실제 AI 응답을 넣어 인증된 프로젝트에서 추천 생성→조회→Preview까지 HTTP로 통과시키는 것이다. 그 전에는 B의 데이터 저장 검증만 성공으로 표시한다.
