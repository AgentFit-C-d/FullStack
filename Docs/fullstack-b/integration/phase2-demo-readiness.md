# Full Stack B Phase 2 데모 준비 상태 (2026-10-17)

## 바로 시연 가능한 검증과 아직 연결되지 않은 흐름

| 구간 | 상태 | 근거 / 남은 일 |
| --- | --- | --- |
| B 추천·Preview·승인·이력·삭제의 UUID 저장 | **검증됨** | `BPhase2UuidSchemaPostgresTest`가 격리된 PostgreSQL에 A Flyway V1–V5를 적용해 추천의 9개 Capability, Preview, 승인자 UUID, B9 export/USER 보고, B 삭제 및 Audit 실패 rollback을 검사한다. |
| B 내부 추천·설정 규칙 회귀 | **검증됨** | 최신 `mvn clean verify`에서 477개 중 실패/오류 0, 건너뜀 3. JDK 24에서 Java 21 release로 빌드했으며 Mockito는 Byte Buddy 시작 에이전트를 사용했다. JDK 21 실행 결과는 아직 없다. |
| A 로그인 → 추천 생성·Preview 공개 API | **UUID HTTP 차단 재현** | 실제 V5 PostgreSQL HTTP 조회에서 A 소유권 저장소의 UUID/String 바인딩 오류로 500이 발생했다. GitHub 계정 조회·생성에도 BIGINT 경로가 남아 있다. [A 인계](phase2-a-uuid-http-blockers.md). |
| 최신 추천 GET | **서비스 검증, HTTP 차단** | B 읽기 포트는 V5 PostgreSQL에서 CURRENT→환경 버전 변경 후 STALE을 반환한다. 실제 HTTP 요청은 A 소유권 저장소의 UUID 바인딩 오류로 500이므로 인증된 프론트 연결 성공은 아직 아니다. [재현 결과](phase2-a-uuid-http-blockers.md). |
| AI Capability 실제 응답 | **차단** | AI 담당: 9종 claim·question·선택/불필요 구분·오류 응답을 OpenAPI와 FastAPI에 구현해 A adapter와 계약 테스트로 맞춰야 한다. 현재는 `CONTRACT_TEST_DOUBLE`이다. |
| 실제 승인 Catalog | **차단** | B/검증 담당: 첫 Client/OS/버전과 도구·권한·정적 템플릿·조합의 실제 검증 근거, 독립 승인 hash가 필요하다. skills.sh 후보를 자동으로 활성화하지 않는다. |
| 승인 카탈로그 GET | **B API·합성 릴리스 검증, 실제 데이터 차단** | 로그인·필터·서버 승인 해시를 확인하는 읽기 전용 API를 연결했다. 실제 Claude Code 검증 릴리스가 없으면 503이며, 프론트 필드 계약은 공통 초안과 맞춰야 한다. [API 현황](phase2-catalog-read-api.md). |
| Preview 저장 시 동시 변경 차단 | **미완** | A 담당: `JdbcPreviewStoreAdapter`의 동일 트랜잭션에서 현재 project/profile/review/developer/environment basis를 재조회·비교하고 동시 수정 테스트를 추가해야 한다. B writer의 추천 basis 검증만으로 A 현재 상태를 보장하지 않는다. |
| 최종 승인·ZIP 다운로드 HTTP | **B 서비스 검증, A HTTP 차단** | B 소유 API가 A의 세션·소유권 검사를 재사용한다. 합성 Catalog를 사용하는 V5 PostgreSQL 서비스 테스트에서 부분 구성 승인, 경고, ZIP, B9/Audit 저장, 환경 변경 차단을 확인했다. 실제 HTTP에서는 공통 A 소유권 저장소 오류부터 해결해야 한다. [API 계약](phase2-finalization-api.md). |
| 다운로드 후 USER 적용 보고 | **B API·DB 연결 검증, A HTTP 차단** | B가 생성 이력 소속·소유자를 확인하고 APPLIED/FAILED 진술과 Audit를 저장한다. 보고는 설치 검증으로 승격하지 않는다. PostgreSQL·Controller 테스트를 수행하며 실제 세션 HTTP 경로는 A UUID 수정 후 재검증한다. [API 계약](phase2-user-report-api.md). |
| 설정 생성 이력 GET | **B API·DB 연결 검증, A HTTP 차단** | B가 생성 메타데이터·USER 보고만 페이지로 반환하고 현재 basis에 대한 CURRENT/STALE을 판정한다. PostgreSQL V5와 Controller 테스트가 통과했으나 실제 세션 HTTP는 A 소유권 UUID 오류 수정 후 재검증해야 한다. [API 계약](phase2-history-api.md). |

## 재현한 검증 환경

- 브랜치: `feat/fullstack-b-phase2`, 기준 `origin/main` `1046f46`.
- 테스트 DB: 초기 검증은 전용 임시 PostgreSQL 17 컨테이너 `agentfit-b-phase2-test`, 최신 검증은 로컬 `agentfit-postgres-local`을 사용했다. PostgreSQL 통합 테스트는 임의의 독립 schema를 만들고 삭제하므로 기존 개발 schema에 V5를 적용하지 않았다.
- 실행: `services/core-api`에서 `AGENTFIT_TEST_POSTGRES_URL`, 사용자, 비밀번호를 전용 DB에 설정하고 Maven `test` 실행. Java 24 사용 시 Surefire JVM에 `-javaagent:<byte-buddy-agent-1.14.19.jar> -Dnet.bytebuddy.experimental=true`를 전달했다.
- 결과: `Tests run: 456, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`. 건너뛴 항목은 Windows에서 심볼릭 링크 생성 권한을 요구하는 Catalog 테스트 1개와 Testcontainers가 Docker를 사용할 수 없다고 판정한 A의 PostgreSQL 동시성 테스트 2개다. 전체 기능 시연 성공을 뜻하지 않는다.
- 후속 B UUID 형식 보강: Java UUID 파서가 허용하는 `1-1-1-1-1` 같은 비정규 표기를 SQL 전에 거부한다. 변경 후 DB 미설정 전체 테스트는 456개 중 실패·오류 0, DB 의존 항목 등 53개 건너뜀이다. 별도 임시 PostgreSQL에서 B UUID/V5 관련 집중 테스트 52개는 건너뜀 없이 모두 통과했다.
- 최신 추천 읽기 연결 후 전체 회귀는 전용 PostgreSQL과 함께 458개 중 실패·오류 0건, 건너뜀 3건이다. 조회 서비스는 반복 읽기 격리 트랜잭션에서 실행한다. 기존 B 조회 SQL의 `FOR SHARE` 때문에 PostgreSQL의 `readOnly=true` 트랜잭션은 사용할 수 없지만, 이 경로는 쓰기 SQL을 실행하지 않는다.
- 최종화 API 추가 후 전용 PostgreSQL을 포함한 전체 Maven 테스트는 465개 중 실패·오류 0건, 건너뜀 3건이다. 이후 저장 시 소유권 재검사를 404로 통일한 수정은 최종화 PostgreSQL·HTTP 집중 테스트로 다시 통과했다. 테스트 Catalog는 합성 릴리스이므로 실제 Client 동작을 증명하지 않는다.
- 2026-10-16 최신 로컬 PostgreSQL 17 회귀는 `Tests run: 464, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`였다. 별도의 A/B 실제 HTTP 재현은 A 소유권 UUID 바인딩 오류로 실패했고, 실패하는 실험 테스트는 커밋하지 않았다. 위의 회귀 성공 수치가 HTTP 연결 성공을 의미하지 않는다.
- B 사용자 적용 보고 API 추가 후 전체 core-api 회귀는 `Tests run: 468, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`였다. PostgreSQL의 격리 스키마에서 보고·Audit 저장과 타 사용자 거부를, Controller 테스트에서 요청 필드 제한과 응답 형식을 확인했다. A 실제 세션 HTTP 경로는 여전히 별도 차단 상태다.
- B 생성 이력 GET 연결 후 로컬 PostgreSQL을 포함한 전체 core-api 회귀는 `Tests run: 472, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`였다. 현재→환경 버전 변경 후 STALE 전환과 적용 보고 조회를 확인했다. 실제 인증된 A/B 종단 HTTP 성공은 아직 아니다.
- B의 Catalog 오류 응답을 503으로 통일하고 승인 카탈로그 GET을 추가한 뒤, 로컬 PostgreSQL 포함 전체 core-api 회귀는 `Tests run: 477, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`였다. Catalog GET 테스트는 합성 릴리스와 MockMvc를 사용했고 실제 인증 세션·Claude Code 릴리스는 확인하지 않았다.
- 2026-10-17 원격 `main`을 fetch한 뒤 로컬 PostgreSQL 17로 `mvn clean verify`를 새로 실행했다. `477`개 중 실패·오류 `0`, 건너뜀 `3`, JAR 패키징 성공이었다. 건너뜀은 Windows 심볼릭 링크 테스트 1개와 Testcontainers가 Docker를 사용할 수 없다고 판정한 A 테스트 2개다. B의 V5 PostgreSQL 테스트는 별도 schema에서 실행됐다.

다음 데모의 성공 기준은 검증된 Catalog와 실제 AI 응답을 넣어 인증된 프로젝트에서 추천 생성→조회→Preview까지 HTTP로 통과시키는 것이다. 그 전에는 B의 데이터 저장 검증만 성공으로 표시한다.
