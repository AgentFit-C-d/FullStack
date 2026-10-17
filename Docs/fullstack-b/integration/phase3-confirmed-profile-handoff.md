# Phase 3: A 확정 프로필 → B 추천 연결

기준 브랜치: `develop`의 A Phase 2 및 B Phase 2 병합본. A는 분석 결과를 DRAFT로 저장하고 사용자 확인 후 CONFIRMED Profile과 확인 이벤트를 생성한다. B는 인증·소유권을 통과한 프로젝트의 최신 CONFIRMED Profile만 읽어 AI needs와 Catalog 판정에 사용한다. DRAFT만 있으면 추천을 시작하지 않는다.

## 현재 연결

1. `POST /api/projects/{projectId}/recommendations`에서 A 세션 인증과 프로젝트 소유권을 검사한다.
2. B 스냅샷은 확정 프로필·확인 이벤트를 필수로 읽고, 개발자·환경·검토 데이터는 있으면 버전과 함께 읽는다. 아직 입력되지 않은 데이터의 버전은 0이다. 기획의 프로젝트명·유형·도메인, 역할·담당 업무 및 허용된 항목만 AI facts로 전달한다.
3. AI needs 응답의 9개 Capability를 검증하고, 서버가 승인한 Catalog 해시의 도구·OS·클라이언트·버전 및 조합만 판정한다.
4. 환경 대상이나 설치 도구 확인 상태가 미정이면 `NEEDS_INFORMATION`과 해당 질문을 저장한다. 설치 목록 미입력은 빈 목록 확인과 구분한다.
5. 저장 트랜잭션은 프로젝트를 잠근 뒤 확정 프로필·확인 이벤트·검토·개발자·환경 버전을 다시 비교한다. 중간 변경이 있으면 추천을 저장하지 않는다. 성공 시 추천과 감사 이벤트를 함께 기록한다.

## 검증 범위와 남은 연결

- 2026-10-17 로컬 Docker에서 Core API·FastAPI·PostgreSQL의 기동과 `/health` 200을 확인했다. 실제 Core의 Spring AI 클라이언트 Bean으로 HMAC `needs` 호출 → B Capability 변환 → 합성 Catalog 판정까지 Java 21 라이브 스모크가 통과했다. Java HTTP 클라이언트의 기본 h2c 업그레이드에서는 FastAPI가 요청 본문을 빈 값으로 받아 401을 반환했고, Core의 AI 전용 클라이언트를 HTTP/1.1로 고정해 해결했다. 이 테스트는 Solar 제공자 API 호출이나 DB 저장을 포함하지 않는다.
- 준비된 로컬 OAuth 설정으로 `GET /api/oauth2/authorization/github`의 GitHub 302 리디렉션을 확인했다. 실제 사용자 로그인 완료·세션 기반 전체 HTTP 흐름은 아직 검증하지 않았다. 설정값과 비밀키는 문서나 로그에 기록하지 않는다.
- 2026-10-17 추가 라이브 통합 테스트에서 격리된 PostgreSQL V1–V6 스키마의 CONFIRMED Profile과 환경 입력을 읽어 실제 FastAPI `needs`를 호출했다. 합성 Catalog로 `RECOMMENDED`를 판정한 뒤 추천 본문, Capability 9건, 추천 도구 1건, 성공 감사 이벤트가 한 DB에 저장됨을 확인했다. 테스트는 `AGENTFIT_LIVE_AI_URL`과 `AGENTFIT_TEST_POSTGRES_URL`이 모두 있을 때만 실행하며 테스트 스키마를 삭제한다. 이는 인증된 브라우저 HTTP 시연이나 Solar 호출을 증명하지 않는다.
- 같은 격리 스키마에서 MockMvc로 추천 생성 POST → 최신 추천 GET(`CURRENT`) → 설정 미리보기 POST와 DB 저장까지 확인했다. 컨트롤러에 주입한 소유자 principal은 테스트 대역이다. 이 결과는 공개 API의 경로·JSON·서비스 연결을 검증하지만 Spring Security 필터를 거친 실제 GitHub OAuth 세션 성공을 의미하지 않는다.

- B 입력 API: `GET /api/projects/{projectId}/recommendation-inputs`, `PUT /api/projects/{projectId}/developer-profile`, `PUT /api/projects/{projectId}/environment-profile`. A 세션·소유권 검사를 통과해야 하며 CONFIRMED Profile 이전에는 저장하지 않는다. 최초 `expectedVersion`은 0이고 응답 버전으로 다음 수정·추천 요청을 만든다. 설치 상태의 `installedComponentsKnown=false`는 미확인이고 `true`와 빈 배열은 설치 도구가 없다고 사용자가 선언한 상태다.
- PostgreSQL V1–V6 마이그레이션에서 DRAFT 차단, CONFIRMED 직후 미입력 상태의 `NEEDS_INFORMATION` 저장, 개발자·환경 입력의 낙관적 버전 검사와 재조회, 환경 설치 상태 UNKNOWN 판정을 검증한다. 로컬 HMAC HTTP 테스트는 AI needs 요청 필드를 검증한다. 테스트 Catalog는 합성 데이터다.
- 실제 A 확인 API → B 추천 HTTP API의 한 번에 이어지는 시연, Solar 실호출, 첫 Claude Code Catalog의 독립 검증·승인, 프론트 입력 화면 연결은 별도 작업이다. 현재 확인되지 않은 Catalog를 실제 지원 도구로 표시하지 않는다.
- A가 `profile_review`를 생성하지 않아도 추천은 가능하며 검토 버전은 0으로 기록한다. 추후 검토가 생기면 확정 프로필 ID·버전에 연결된 검토만 적용한다.
